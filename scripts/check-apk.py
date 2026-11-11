#!/usr/bin/env python3
"""核对最终 APK 的离线、无桌面入口约定及 ARM64 / 16 KB 对齐，输出 JSON 证据。"""
import argparse
import hashlib
import json
import os
import re
import shutil
import struct
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NETWORK_SYMBOLS = {
    "socket", "connect", "bind", "listen", "accept", "accept4", "send", "sendto", "sendmsg",
    "sendmmsg", "recv", "recvfrom", "recvmsg", "recvmmsg", "getaddrinfo", "getnameinfo",
    "gethostbyname", "gethostbyname2", "gethostbyaddr", "res_query", "res_search",
}


def elf_network_imports(data):
    """ELF64 小端动态符号；不把本地文件复制 sendfile 当作建立网络连接。"""
    offset = struct.unpack_from("<Q", data, 40)[0]
    size, count = struct.unpack_from("<HH", data, 58)
    sections = [struct.unpack_from("<IIQQQQIIQQ", data, offset + i * size) for i in range(count)]
    found = set()
    for section in sections:
        if section[1] != 11:  # SHT_DYNSYM
            continue
        strings_section = sections[section[6]]
        strings = data[strings_section[4]:strings_section[4] + strings_section[5]]
        for pos in range(section[4], section[4] + section[5], section[9]):
            name, info, other, index, value, symbol_size = struct.unpack_from("<IBBHQQ", data, pos)
            if index != 0 or not name:  # 仅未定义的导入符号
                continue
            symbol = strings[name:strings.index(b"\0", name)].decode("ascii", errors="replace")
            base = symbol.removeprefix("__").removesuffix("_chk")
            if base in NETWORK_SYMBOLS or symbol.startswith(("curl_", "SSL_", "BIO_connect")):
                found.add(symbol)
    return sorted(found)


def dex_network_references(data):
    """检查 DEX 方法表中的常见网络、远程语音和下载入口，不等同于形式化代码证明。"""
    if data[:4] != b"dex\n":
        raise ValueError("Unsupported DEX format")
    string_count, string_offset, type_count, type_offset = struct.unpack_from("<IIII", data, 56)
    strings = []
    for i in range(string_count):
        pos = struct.unpack_from("<I", data, string_offset + i * 4)[0]
        while data[pos] & 128:
            pos += 1
        pos += 1  # ULEB128 的字符串长度
        strings.append(data[pos:data.index(b"\0", pos)].decode("utf-8", errors="replace"))
    types = [strings[struct.unpack_from("<I", data, type_offset + i * 4)[0]] for i in range(type_count)]
    method_count, method_offset = struct.unpack_from("<II", data, 88)
    found = set()
    prefixes = (
        "Ljava/net/Socket", "Ljava/net/ServerSocket", "Ljava/net/Datagram", "Ljava/net/MulticastSocket",
        "Ljava/net/InetAddress;", "Ljava/net/URLConnection;", "Ljava/net/HttpURLConnection;",
        "Ljavax/net/", "Ljava/nio/channels/SocketChannel", "Ljava/nio/channels/ServerSocketChannel",
        "Ljava/nio/channels/DatagramChannel", "Ljava/nio/channels/AsynchronousSocketChannel",
        "Ljava/nio/channels/AsynchronousServerSocketChannel", "Landroid/webkit/WebView",
        "Landroid/app/DownloadManager", "Landroid/speech/SpeechRecognizer", "Landroid/net/http/",
        "Lokhttp3/", "Lcom/squareup/okhttp/", "Lretrofit2/", "Lorg/apache/http/",
        "Lcom/google/firebase/", "Lcom/google/android/gms/analytics/",
    )
    for i in range(method_count):
        cls, proto, name = struct.unpack_from("<HHI", data, method_offset + i * 8)
        owner, method = types[cls], strings[name]
        network = owner.startswith(prefixes)
        network |= owner == "Ljava/net/URL;" and method in {"openStream", "openConnection", "getContent"}
        network |= owner == "Landroid/system/Os;" and method in NETWORK_SYMBOLS
        network |= owner == "Landroid/net/Network;" and method in {"openConnection", "getSocketFactory", "bindSocket"}
        if network:
            found.add(owner + "->" + method)
    return sorted(found)


def find_aapt(explicit=None):
    if explicit:
        return str(explicit)
    candidates = [ROOT / ".toolchain/android-sdk/build-tools/36.0.0/aapt"]
    for env in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(env):
            candidates += sorted((Path(os.environ[env]) / "build-tools").glob("*/aapt"), reverse=True)
    for candidate in candidates:
        if candidate.is_file():
            return str(candidate)
    if shutil.which("aapt"):
        return shutil.which("aapt")
    raise FileNotFoundError("需要 Android Build Tools 的 aapt；设置 ANDROID_HOME 或指定 --aapt")


def manifest_policy(path, aapt):
    def dump(*args):
        return subprocess.run([aapt, "dump", *args], check=True, text=True, capture_output=True).stdout
    permissions = re.findall(r"^uses-permission(?:-sdk-\d+)?: name='([^']+)'", dump("permissions", str(path)), re.M)
    manifest = dump("xmltree", str(path), "AndroidManifest.xml")
    badging = dump("badging", str(path))
    return dict(permissions=permissions,
                launcher_entry=bool(re.search(r"android\.intent\.category\.[A-Z_]*LAUNCHER", manifest)
                                    or "launchable-activity:" in badging),
                shared_uid="android:sharedUserId(" in manifest)


def check(path, aapt=None):
    libraries = []
    dex = []
    errors = []
    policy = manifest_policy(path, find_aapt(aapt))
    if set(policy["permissions"]) != {"android.permission.RECORD_AUDIO"}:
        errors.append("Only microphone permission is allowed: " + str(policy["permissions"]))
    if policy["launcher_entry"]:
        errors.append("Launcher entry is forbidden")
    if policy["shared_uid"]:
        errors.append("Shared UID is forbidden")
    with zipfile.ZipFile(path) as archive, path.open("rb") as raw:
        for info in archive.infolist():
            if re.fullmatch(r"classes\d*\.dex", info.filename):
                references = dex_network_references(archive.read(info))
                dex.append(dict(name=info.filename, network_api_references=references))
                if references:
                    errors.append("Network API references found: " + info.filename)
            if not info.filename.startswith("lib/") or not info.filename.endswith(".so"):
                continue
            data = archive.read(info)
            if not info.filename.startswith("lib/arm64-v8a/"):
                errors.append("Unexpected ABI: " + info.filename)
            if data[:6] != b"\x7fELF\x02\x01":
                errors.append("Not little-endian ELF64: " + info.filename)
                continue
            machine = struct.unpack_from("<H", data, 18)[0]
            phoff = struct.unpack_from("<Q", data, 32)[0]
            phsize, phcount = struct.unpack_from("<HH", data, 54)
            loads = []
            for i in range(phcount):
                fields = struct.unpack_from("<IIQQQQQQ", data, phoff + i * phsize)
                kind, flags, offset, address, physical, filesz, memsz, alignment = fields
                if kind == 1:
                    loads.append(alignment)
                    if alignment < 16384 or (address - offset) % 16384:
                        errors.append("Invalid 16 KB LOAD alignment: " + info.filename)
            if machine != 183 or not loads:
                errors.append("Invalid AArch64 ELF: " + info.filename)
            raw.seek(info.header_offset)
            header = raw.read(30)
            name_length, extra_length = struct.unpack_from("<HH", header, 26)
            payload = info.header_offset + 30 + name_length + extra_length
            if info.compress_type != zipfile.ZIP_STORED or payload % 16384:
                errors.append("Native library is not stored at a 16 KB ZIP boundary: " + info.filename)
            imports = elf_network_imports(data)
            if imports:
                errors.append("Native network imports found: " + info.filename)
            libraries.append(dict(name=info.filename, elf=64, machine=machine, load_alignments=loads, network_imports=imports,
                                  zip_offset=payload, sha256=hashlib.sha256(data).hexdigest()))
        for required in ("assets/speech/model.int8.onnx", "assets/speech/tokens.txt", "assets/rime/default.yaml"):
            if required not in archive.namelist():
                errors.append("Missing offline asset: " + required)
    if not libraries:
        errors.append("No native libraries")
    if not dex:
        errors.append("No DEX files")
    return dict(apk=str(path), bytes=path.stat().st_size, sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
                manifest_policy=policy, dex=dex, native_libraries=libraries, errors=errors, passed=not errors)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--aapt", type=Path)
    args = parser.parse_args()
    result = check(args.apk, args.aapt)
    rendered = json.dumps(result, ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered + "\n")
    print(rendered)
    raise SystemExit(0 if result["passed"] else 1)
