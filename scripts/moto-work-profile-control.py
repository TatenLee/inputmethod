#!/usr/bin/env python3
"""Moto X40 / Android 16：复现已验证的工作空间横条修复，并保留恢复记录。"""
import argparse
import importlib.util
from pathlib import Path
import shlex
import shutil
import subprocess

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("moto_profile", HERE / "moto-work-profile.py")
moto = importlib.util.module_from_spec(spec)
spec.loader.exec_module(moto)
TEST_PACKAGE = "indi.tatenlee.workprofile.test"
HELPER_PACKAGE = "indi.tatenlee.workprofile"
RUNNER = TEST_PACKAGE + "/indi.tatenlee.workprofile.control.WorkProfileControl"
BRIDGE = "indi.tatenlee.workprofile.control.OverlayShellBridge"


def find_adb(explicit):
    if explicit:
        return explicit
    for directory in [HERE, *HERE.parents]:
        path = directory / ".toolchain/android-sdk/platform-tools/adb"
        if path.is_file():
            return str(path)
    return shutil.which("adb") or str(Path.home() / "Library/Android/sdk/platform-tools/adb")


def apk_paths():
    packaged = (HERE / "work-profile-helper-debug.apk", HERE / "work-profile-helper-debug-androidTest.apk")
    if all(path.is_file() for path in packaged):
        return packaged
    root = HERE.parent / "work-profile-helper/build/outputs/apk"
    paths = (root / "debug/work-profile-helper-debug.apk", root / "androidTest/debug/work-profile-helper-debug-androidTest.apk")
    moto.require(all(path.is_file() for path in paths),
                 "缺少工具 APK。先执行 python3 scripts/run-gradle.py :work-profile-helper:assembleDebug :work-profile-helper:assembleDebugAndroidTest，或使用完整备份目录。")
    return paths


def validate_target(identity, users, current_user):
    moto.require(identity.get("manufacturer", "").lower() == "motorola"
                 and identity.get("model") == "XT2301-5" and identity.get("sdk") == "36",
                 "本工具只验证过 Moto X40（XT2301-5）Android 16，拒绝用于其他设备。")
    work = users.get("12", {})
    moto.require(current_user == 0 and work.get("managed") is True and work.get("parent") == 0
                 and work.get("serial") == 12, "工作空间身份已变化；不能仅凭 user ID 使用旧配置。")


def validate_backup(plan, identity, users):
    moto.require(plan.get("format") == 1 and plan.get("kind") == "moto-hide-overlay-plan"
                 and plan.get("overlay") == moto.HIDE_OVERLAY and plan.get("before") is False
                 and plan.get("after") is True and plan.get("personal_user") == 0,
                 "不是本工具支持的导航横条恢复记录。")
    moto.require(plan.get("device") == identity, "备份属于其他手机或系统版本，拒绝修改。")
    work = plan.get("work_user", {})
    moto.require(work.get("id") == 12 and work.get("serial") == users["12"]["serial"]
                 and work.get("parent") == 0 and work.get("managed") is True, "备份中的工作空间已失效。")


def run(device, args, timeout=90):
    result = subprocess.run([device.adb, "-s", device.serial, *args], capture_output=True, text=True, timeout=timeout)
    moto.require(result.returncode == 0, result.stderr.strip() or result.stdout.strip() or "ADB 命令失败")
    return result.stdout.strip()


def require_runner_success(output):
    moto.require("INSTRUMENTATION_CODE: -1" in output and "INSTRUMENTATION_RESULT: error=" not in output,
                 "维护组件未完成操作，请检查系统状态后再重试：\n" + output)


def execute_overlay_action(device, action):
    helper, test = apk_paths()
    # 测试组件只安装到主空间；不会接管 Shelter，也不在工作空间启动 instrumentation。
    query = subprocess.run([device.adb, "-s", device.serial, "shell", "pm", "path", "--user", "0", HELPER_PACKAGE],
                           capture_output=True, text=True, timeout=30)
    moto.require(query.returncode in (0, 1) and not query.stderr.strip(), "无法检查辅助工具安装状态：" + query.stderr)
    if not query.stdout.startswith("package:"):
        moto.require("Success" in run(device, ["install", "--user", "0", "-r", str(helper)]), "辅助工具安装失败")
    test_installed = False
    try:
        moto.require("Success" in run(device, ["install", "--user", "0", "-r", "-t", str(test)]), "临时组件安装失败")
        test_installed = True
        path = device.shell("pm", "path", "--user", "0", TEST_PACKAGE).removeprefix("package:")
        moto.require(path.startswith("/data/app/") and "\n" not in path, "测试组件安装路径异常")
        output = run(device, ["shell", "CLASSPATH=" + shlex.quote(path) + " app_process /system/bin " + BRIDGE])
        moto.require("Overlay reference delivered: true" in output, "无法连接系统导航资源服务")
        output = device.shell("am", "instrument", "--user", "0", "--no-hidden-api-checks", "-w", "-r",
                              "-e", "action", action, "-e", "work_user", "12", "-e", "work_serial", "12", RUNNER)
        require_runner_success(output)
        print(output)
    finally:
        # 临时授权由 runner 的 finally 撤销；删除测试 APK，避免留下维护入口。
        if test_installed:
            cleanup = run(device, ["shell", "pm", "uninstall", "--user", "0", TEST_PACKAGE])
            moto.require("Success" in cleanup, "临时组件清理失败：" + cleanup)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("inspect", "hide", "restore"))
    parser.add_argument("--adb", help="ADB 可执行文件；默认查找项目工具链、PATH 和 Android SDK")
    parser.add_argument("--serial", help="ADB 序列号；默认只选唯一已授权的实体手机")
    parser.add_argument("--backup", type=Path, help="恢复记录；hide 可新建，restore 必须使用已有记录")
    args = parser.parse_args()
    device = moto.Device(find_adb(args.adb), args.serial)
    identity, users = device.identity(), device.users()
    validate_target(identity, users, int(device.shell("am", "get-current-user")))
    states = device.hide_overlay_states()
    moto.require(states.get("0", {}).get("enabled") is True and isinstance(states.get("12", {}).get("enabled"), bool),
                 "请先在主空间保持导航栏隐藏，并确认工作空间的 Moto 导航资源可用。")
    print("主空间隐藏：开启；工作空间隐藏：" + ("开启" if states["12"]["enabled"] else "关闭"), flush=True)
    if args.command == "inspect":
        print("工作空间禁止普通 ADB 调试：" + str(users["12"].get("debugging_blocked", False)))
        return
    moto.require(args.backup is not None, "hide / restore 必须指定 --backup，恢复记录不会被覆盖。")
    desired = args.command == "hide"
    if args.backup.exists():
        plan = moto.load(args.backup)
        validate_backup(plan, identity, users)
    else:
        moto.require(desired and states["12"]["enabled"] is False, "无法推断修改前状态；请使用已有恢复记录。")
        plan = moto.make_overlay_plan(device, 12)
        moto.save(args.backup, plan)
        print("原状态已保存：" + str(args.backup), flush=True)
    if states["12"]["enabled"] == desired:
        print("已经是目标状态，没有重复修改。")
        return
    execute_overlay_action(device, "hide-work-pill" if desired else "restore-work-pill")
    after = device.hide_overlay_states()
    moto.require(after.get("12", {}).get("enabled") == desired, "写入后读回不一致，请保留备份并核查。")
    moto.require({k: v for k, v in after.items() if k != "12"} == {k: v for k, v in states.items() if k != "12"},
                 "其他空间的导航配置发生了变化，请核查。")
    moto.require(device.users()["12"].get("debugging_blocked") == users["12"].get("debugging_blocked"), "调试限制状态发生变化，请核查。")
    print("已" + ("隐藏" if desired else "恢复显示") + "工作空间横条；请收起并重新打开键盘确认。临时组件已移除。")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError, subprocess.SubprocessError) as error:
        raise SystemExit(str(error))
