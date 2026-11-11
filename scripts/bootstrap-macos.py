#!/usr/bin/env python3
"""在项目 .toolchain 中安装固定版本的 macOS ARM64 工具链，不修改系统配置。"""
import argparse
import concurrent.futures
import subprocess
import sys
from pathlib import Path
from fetch import download, digest

ROOT = Path(__file__).resolve().parent.parent
TOOLS = ROOT / ".toolchain"
SDK = TOOLS / "android-sdk"
JDK_URL = "https://cdn.azul.com/zulu/bin/zulu17.70.113-ca-jdk17.0.21.0.3-macosx_aarch64.tar.gz"
JDK_SHA = "f4a4d641d4441f480939176208f333aa9403e0a68d27e87245cec98cc994f943"
PACKAGES = [
    ("platform-36_r02.zip", "2c1a80dd4d9f7d0e6dd336ec603d9b5c55a6f576", "platforms/android-36"),
    ("build-tools_r36_macosx.zip", "199ae0047ee61e842f8ee0c6d3918e44fb9a1f83", "build-tools/36.0.0"),
    ("cmake-3.22.1-darwin.zip", "8604eeef9adadb626dbb70a7ff58a87e6a7b967a", "cmake/3.22.1"),
    ("platform-tools_r37.0.1-darwin.zip", "6ae73f4de6452dc57e62ec02b68eed92a4c21661", "platform-tools"),
    ("android-ndk-r29-darwin.zip", "03d29fbb57e3c05a7d53597dd011d856c1456a4f", "ndk/29.0.14206865"),
]


def install_package(spec, existing=False):
    name, checksum, directory = spec
    destination = SDK / directory
    archive = TOOLS / "downloads" / name
    if existing:
        if not archive.exists() or digest(archive, "sha1") != checksum:
            raise ValueError("Missing or invalid archive: " + name)
    else:
        download("https://dl.google.com/android/repository/" + name, archive, checksum, "sha1", workers=4)
    if destination.exists():
        return
    temporary = TOOLS / ("extract-" + name)
    temporary.mkdir(exist_ok=True)
    subprocess.run(["unzip", "-qo", str(archive), "-d", str(temporary)], check=True)
    children = [p for p in temporary.iterdir() if p.name != "__MACOSX"]
    source = children[0] if len(children) == 1 and children[0].is_dir() else temporary
    destination.parent.mkdir(parents=True, exist_ok=True)
    source.rename(destination)
    print("Installed " + directory, flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--existing-downloads", action="store_true")
    args = parser.parse_args()
    if sys.platform != "darwin":
        raise SystemExit("其他系统请安装 JDK 17、SDK 36、NDK 29.0.14206865、CMake 3.22.1。")
    archive = TOOLS / "downloads/zulu17.tar.gz"
    if args.existing_downloads:
        if not archive.exists() or digest(archive) != JDK_SHA:
            raise ValueError("Missing or invalid JDK archive")
    else:
        download(JDK_URL, archive, JDK_SHA)
    java = TOOLS / "java"
    java.mkdir(parents=True, exist_ok=True)
    if not list(java.glob("**/bin/javac")):
        subprocess.run(["tar", "-xzf", str(archive), "-C", str(java)], check=True)
    jdk = next(java.glob("**/bin/javac")).parent.parent
    link = TOOLS / "jdk"
    if not link.exists():
        link.symlink_to(jdk.relative_to(TOOLS), target_is_directory=True)
    with concurrent.futures.ThreadPoolExecutor(3) as pool:
        list(pool.map(lambda spec: install_package(spec, args.existing_downloads), PACKAGES))
    (ROOT / "local.properties").write_text("sdk.dir=" + str(SDK) + "\n")
    print("Toolchain ready. Next: python3 scripts/prepare.py && scripts/build.sh", flush=True)


if __name__ == "__main__":
    main()
