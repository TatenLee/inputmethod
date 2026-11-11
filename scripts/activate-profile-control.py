#!/usr/bin/env python3
"""为简心·工作空间 APK 激活本次控制连接；不修改任何导航设置。"""
import argparse
from pathlib import Path
import shlex
import shutil
import subprocess
import time

PACKAGE = "indi.taten.workprofile"
HERE = Path(__file__).resolve().parent


def locate_adb(value):
    if value:
        return value
    for parent in [HERE, *HERE.parents]:
        p = parent / ".toolchain/android-sdk/platform-tools/adb"
        if p.is_file(): return str(p)
    return shutil.which("adb") or str(Path.home() / "Library/Android/sdk/platform-tools/adb")


def locate_apk(value):
    if value: return Path(value)
    candidates = [HERE / "profile-control-0.2.0-offline-debug.apk",
                  HERE.parent / "dist/profile-control-0.2.0-offline-debug.apk",
                  HERE / "profile-control-0.1.0-offline-debug.apk",
                  HERE.parent / "dist/profile-control-0.1.0-offline-debug.apk",
                  HERE.parent / "profile-control/build/outputs/apk/debug/profile-control-debug.apk"]
    for p in candidates:
        if p.is_file(): return p
    raise ValueError("找不到控制 APK，请把脚本与 APK 放在同一目录，或使用 --apk 指定。")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb")
    parser.add_argument("--serial", help="多个设备时显式指定；默认不选择模拟器")
    parser.add_argument("--apk")
    args = parser.parse_args()
    adb = locate_adb(args.adb)
    serial = args.serial
    if not serial:
        result = subprocess.run([adb, "devices"], capture_output=True, text=True, check=True)
        matches = [l.split()[0] for l in result.stdout.splitlines()[1:]
                   if len(l.split()) >= 2 and l.split()[1] == "device" and not l.startswith("emulator-")]
        if len(matches) != 1: raise ValueError("请连接并授权一台手机；多设备时指定 --serial。")
        serial = matches[0]
    base = [adb, "-s", serial]
    def run(args, timeout=60, check=True):
        result = subprocess.run(base + args, capture_output=True, text=True, timeout=timeout)
        if check and result.returncode:
            raise ValueError(result.stderr.strip() or result.stdout.strip())
        return result.stdout.strip()
    def shell(*args, check=True): return run(["shell", shlex.join(map(str, args))], check=check)
    sdk = int(shell("getprop", "ro.build.version.sdk"))
    if sdk < 31: raise ValueError("控制工具要求 Android 12 或更高版本。")
    user = int(shell("am", "get-current-user"))
    if user < 0: raise ValueError("无法确定当前主用户。")
    apk = locate_apk(args.apk)
    if "Success" not in run(["install", "--user", str(user), "-r", str(apk)]): raise ValueError("APK 安装失败")
    print("已安装控制 APK，正在激活…", flush=True)
    started = False
    try:
        shell("am", "force-stop", "--user", user, PACKAGE)
        # -w 为 UiAutomation 提供会话；nohup 让手机端会话在拔掉 USB 后继续存活。
        # 日志只包含本工具状态，既不监听输入文字，也不读取短信。
        command = ["nohup", "am", "instrument", "--user", str(user), "--no-hidden-api-checks", "-w", "-r",
                   PACKAGE + "/.control.ControlInstrumentation"]
        run(["shell", shlex.join(command) + " </dev/null >/data/local/tmp/indi.taten.workprofile-session.log 2>&1 &"])
        started = True
        deadline = time.monotonic() + 25
        active = False
        while time.monotonic() < deadline:
            # 不提前访问 Provider：访问会抢先启动普通进程，导致本次隐藏 API 授权没有生效。
            status = shell("cat", "/data/local/tmp/indi.taten.workprofile-session.log", check=False)
            if "control_active=true" in status:
                active = True; break
            if "INSTRUMENTATION_CODE:" in status:
                break
            time.sleep(0.3)
        if not active:
            log = shell("cat", "/data/local/tmp/indi.taten.workprofile-session.log", check=False)
            raise ValueError("本机未能启动控制会话：\n" + log[-4000:])
        path = shell("pm", "path", "--user", user, PACKAGE).removeprefix("package:")
        if not path.startswith("/data/app/") or "\n" in path: raise ValueError("控制 APK 安装路径异常")
        command = "CLASSPATH=" + shlex.quote(path) + " app_process /system/bin " + PACKAGE + ".control.OverlayShellBridge " + str(user)
        if "PROFILE_CONTROL_READY" not in run(["shell", command]): raise ValueError("系统服务连接失败")
        shell("am", "start", "--user", user, "-n", PACKAGE + "/.MainActivity")
        print("激活完成。可拔掉 USB，在手机 APK 中管理工作应用权限或切换横条。")
        print("激活本身没有修改导航设置；重启或进程结束后需要再次运行此脚本。")
    except Exception:
        if started: shell("am", "force-stop", "--user", user, PACKAGE, check=False)
        raise


if __name__ == "__main__":
    try: main()
    except (OSError, ValueError, subprocess.SubprocessError) as error: raise SystemExit(str(error))
