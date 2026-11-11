#!/usr/bin/env python3
"""使用项目工具链和系统已有的 HTTP 代理运行 Gradle；不写入全局配置。"""
import os
import subprocess
import sys
import urllib.parse
import urllib.request
from pathlib import Path

root = Path(__file__).resolve().parent.parent
env = dict(os.environ)
jdk = root / ".toolchain/jdk"
sdk = root / ".toolchain/android-sdk"
if jdk.exists():
    env["JAVA_HOME"] = str(jdk)
if sdk.exists():
    env["ANDROID_HOME"] = str(sdk)
installed = root / ".toolchain/gradle-8.13/bin/gradle"
args = [str(installed if installed.exists() else root / "gradlew")]
for scheme in ("http", "https"):
    value = urllib.request.getproxies().get(scheme)
    if value:
        proxy = urllib.parse.urlparse(value)
        args += [f"-D{scheme}.proxyHost={proxy.hostname}", f"-D{scheme}.proxyPort={proxy.port}"]
args += sys.argv[1:]
raise SystemExit(subprocess.call(args, cwd=root, env=env))
