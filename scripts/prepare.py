#!/usr/bin/env python3
"""固定版本准备源码、语音和词库；不会跟随 CyIME/main 自动更新。"""
import argparse
import concurrent.futures
import json
import os
import shutil
import subprocess
import tarfile
import urllib.request
from pathlib import Path
from fetch import download, digest

ROOT = Path(__file__).resolve().parent.parent
LOCK = json.loads((ROOT / "third_party/versions.json").read_text())


def git(*args):
    env = dict(os.environ, GIT_TERMINAL_PROMPT="0")
    for key in ("http", "https"):
        proxy = urllib.request.getproxies().get(key)
        if proxy:
            env.setdefault(key.upper() + "_PROXY", proxy)
    subprocess.run(["git", "-c", "http.version=HTTP/1.1", *map(str, args)], cwd=ROOT, env=env, check=True)


def repository(name, spec, sparse=None):
    path = ROOT / "third_party" / name
    if not (path / ".git").exists():
        git("clone", "--depth", "1", "--filter=blob:none", "--no-checkout", spec["url"], path)
    head = subprocess.run(["git", "-C", str(path), "rev-parse", "HEAD"], capture_output=True, text=True).stdout.strip()
    if head != spec["revision"]:
        git("-C", path, "fetch", "--depth", "1", "origin", spec["revision"])
        if sparse:
            git("-C", path, "sparse-checkout", "set", "--no-cone", *["/" + f for f in sparse])
        git("-C", path, "checkout", "--detach", spec["revision"])
    if spec.get("submodules"):
        git("-C", path, "submodule", "update", "--init", "--depth", "1", "--jobs", "4", *spec["submodules"])
        for name, commit in spec["submodules"].items():
            actual = subprocess.check_output(["git", "-C", str(path / name), "rev-parse", "HEAD"], text=True).strip()
            if actual != commit:
                raise ValueError(f"Wrong submodule version: {name}")
    return path


def extract_boost(archive):
    target = ROOT / "third_party/boost"
    if (target / "boost/version.hpp").exists():
        return
    # 只解压需要的头文件与许可；ZIP/tar 中的路径不直接决定输出位置。
    target.mkdir(parents=True, exist_ok=True)
    with tarfile.open(archive) as source:
        for member in source:
            parts = Path(member.name).parts[1:]
            if not parts or ".." in parts or not member.isfile():
                continue
            relative = Path(*parts)
            if parts[0] != "boost" and relative.name != "LICENSE_1_0.txt":
                continue
            output = target / relative
            output.parent.mkdir(parents=True, exist_ok=True)
            with source.extractfile(member) as src, output.open("wb") as dst:
                shutil.copyfileobj(src, dst)


def apply_rime_patches():
    repository = ROOT / "third_party/librime"
    for spec in LOCK["rime"].get("patches", []):
        patch = ROOT / "third_party" / spec["file"]
        if digest(patch) != spec["sha256"]:
            raise ValueError("Patch checksum mismatch: " + str(patch))
        reverse = subprocess.run(["git", "-C", str(repository), "apply", "--reverse", "--check", str(patch)],
                                 capture_output=True)
        if reverse.returncode == 0:
            continue
        git("-C", repository, "apply", "--check", patch)
        git("-C", repository, "apply", patch)


def assets():
    output = ROOT / "app/build/generated/assets"
    rime = output / "rime"
    rime.mkdir(parents=True, exist_ok=True)
    shutil.copytree(ROOT / "engine-rime/schemas", rime, dirs_exist_ok=True)
    # 隐私输入使用禁用用户词库的独立方案；不依赖引擎未实现的运行时开关。
    for name in ("jianxin_pinyin", "jianxin_t9"):
        original = (rime / (name + ".schema.yaml")).read_text()
        private = original.replace("schema_id: " + name, "schema_id: " + name + "_private")
        private = private.replace("enable_user_dict: true", "enable_user_dict: false")
        (rime / (name + "_private.schema.yaml")).write_text(private)
    default = rime / "default.yaml"
    default.write_text(default.read_text().replace("menu:\n", "  - schema: jianxin_pinyin_private\n  - schema: jianxin_t9_private\nmenu:\n", 1))
    for name in ("8105", "base", "ext"):
        destination = rime / "cn_dicts" / (name + ".dict.yaml")
        destination.parent.mkdir(exist_ok=True)
        shutil.copy2(ROOT / "third_party/rime-ice/cn_dicts" / destination.name, destination)
    # Revision 随方案和字典内容改变，避免升级后继续使用旧的部署结果。
    import hashlib
    revision = hashlib.sha256()
    for file in sorted(rime.rglob("*.yaml")):
        revision.update(file.relative_to(rime).as_posix().encode())
        revision.update(file.read_bytes())
    (rime / "revision.txt").write_text(revision.hexdigest())
    speech = output / "speech"
    speech.mkdir(exist_ok=True)
    for spec in LOCK["speech"]:
        source = ROOT / ".downloads/speech" / spec["name"]
        if digest(source) != spec["sha256"]:
            raise ValueError("Invalid speech model: " + str(source))
        shutil.copy2(source, speech / spec["name"])
    licenses = output / "licenses"
    licenses.mkdir(exist_ok=True)
    files = {
        "Jianxin-GPL-3.0.txt": ROOT / "LICENSE",
        "Rime-BSD-3-Clause.txt": ROOT / "third_party/librime/LICENSE",
        "Rime-Ice-GPL-3.0.txt": ROOT / "third_party/rime-ice/LICENSE",
        "Boost-1.0.txt": ROOT / "third_party/boost/LICENSE_1_0.txt",
        "LevelDB-BSD.txt": ROOT / "third_party/librime/deps/leveldb/LICENSE",
        "Marisa.txt": ROOT / "third_party/librime/deps/marisa-trie/COPYING.md",
        "OpenCC-Apache-2.0.txt": ROOT / "third_party/librime/deps/opencc/LICENSE",
        "yaml-cpp-MIT.txt": ROOT / "third_party/librime/deps/yaml-cpp/LICENSE",
        "Third-party-notices.txt": ROOT / "THIRD_PARTY_NOTICES.md",
    }
    for name, source in files.items():
        shutil.copy2(source, licenses / name)
    shutil.copytree(ROOT / "third_party/licenses", licenses, dirs_exist_ok=True)
    print("Prepared bundled dictionary, offline speech and licenses", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--assets-only", action="store_true")
    args = parser.parse_args()
    if not args.assets_only:
        repository("librime", LOCK["rime"])
        repository("rime-ice", LOCK["dictionary"], LOCK["dictionary"]["files"])
        boost = download(LOCK["boost"]["url"], ROOT / ".toolchain/downloads/boost-1.89.0.tar.bz2", LOCK["boost"]["sha256"])
        extract_boost(boost)
        download(LOCK["sherpa"]["url"], ROOT / ".downloads/libraries/sherpa-onnx-1.13.8.aar", LOCK["sherpa"]["sha256"])
        for spec in LOCK["speech"]:
            download(spec["url"], ROOT / ".downloads/speech" / spec["name"], spec["sha256"])
    apply_rime_patches()
    assets()


if __name__ == "__main__":
    main()
