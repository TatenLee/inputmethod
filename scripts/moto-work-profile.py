#!/usr/bin/env python3
"""通过 USB ADB 比较 Moto 导航设置，并将已识别的隐藏选项同步到工作空间；支持恢复。"""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
HIDE_OVERLAY = "com.android.internal.systemui.navbar.hidegestural"
RELEVANT = re.compile(r"nav|gesture|pill|keyboard|input_method|ime_", re.I)
VISIBILITY = re.compile(r"(?:nav|gesture|pill).*(?:hide|hidden|show|visible|visibility|hint|handle)|"
                        r"(?:hide|hidden|show|visible|visibility|hint|handle).*(?:nav|gesture|pill)", re.I)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def save(path, value):
    """先独占创建备份再写设备，避免覆盖上一份恢复记录。"""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with os.fdopen(os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "w") as output:
        json.dump(value, output, ensure_ascii=False, indent=2)
        output.write("\n")
        output.flush()
        os.fsync(output.fileno())


def load(path):
    return json.loads(Path(path).read_text())


def parse_users(text):
    text = text.split("\nDevice properties:", 1)[0]
    users = {}
    pattern = r"(?m)^\s*UserInfo\{(\d+):.*?:([0-9a-fA-F]+)\}([^\n]*)\n(.*?)(?=^\s*UserInfo\{|\Z)"
    for match in re.finditer(pattern, text, re.M | re.S):
        user_id, flags, header, block = match.groups()
        serial = re.search(r"serialNo=(\d+)", header)
        kind = re.search(r"(?m)^\s+Type:\s*(\S+)", block)
        group = re.search(r"(?:parentId|profileGroupId|Profile group id)\s*[=:]\s*(-?\d+)", header + block, re.I)
        users[user_id] = dict(id=int(user_id), serial=int(serial[1]) if serial else None,
                              type=kind[1] if kind else None,
                              managed=bool(int(flags, 16) & 0x20),
                              debugging_blocked=bool(re.search(r"(?m)^\s+no_debugging_features\s*$", block)),
                              parent=int(group[1]) if group else None)
    require(users, "无法解析用户列表；没有修改设备。")
    return users


def state(values, key):
    return dict(exists=key in values, value=values.get(key))


def valid_state(value):
    return isinstance(value, dict) and ((value.get("exists") is False and value.get("value") is None)
                                       or (value.get("exists") is True and value.get("value") in ("0", "1", "true", "false")))


class Device:
    def __init__(self, adb=None, serial=None):
        local = ROOT / ".toolchain/android-sdk/platform-tools/adb"
        self.adb = adb or (str(local) if local.is_file() else shutil.which("adb"))
        require(self.adb, "找不到 adb；先准备项目工具链或安装 Android Platform Tools。")
        if not serial:
            output = subprocess.run([self.adb, "devices"], capture_output=True, text=True, check=True).stdout
            devices = [line.split()[0] for line in output.splitlines()[1:]
                       if len(line.split()) >= 2 and line.split()[1] == "device" and not line.startswith("emulator-")]
            require(len(devices) == 1, "请连接并授权一台手机的 USB 调试；多设备时用 --serial 指定。不会自动选择模拟器。")
            serial = devices[0]
        self.serial = serial

    def shell(self, *args):
        # adb shell 仍会交给设备端 shell；逐参数转义，不把配置值当成代码。
        result = subprocess.run([self.adb, "-s", self.serial, "shell", shlex.join(map(str, args))],
                                capture_output=True, text=True, timeout=30)
        output = result.stdout.strip()
        require(result.returncode == 0 and not re.search(r"(?m)^(?:Error|Exception|Security exception|Permission denial)", output),
                "ADB 操作失败：" + (result.stderr.strip() or output))
        return output

    def identity(self):
        props = {key: self.shell("getprop", "ro." + prop) for key, prop in {
            "manufacturer": "product.manufacturer", "model": "product.model",
            "android": "build.version.release", "sdk": "build.version.sdk", "build": "build.fingerprint",
        }.items()}
        return dict(serial=self.serial, **props)

    def users(self):
        return parse_users(self.shell("dumpsys", "user"))

    def hide_overlay_states(self):
        return parse_hide_overlays(self.shell("dumpsys", "overlay"))

    def write_hide_overlay(self, user, enabled):
        self.shell("cmd", "overlay", "enable" if enabled else "disable", "--user", user, HIDE_OVERLAY)
        require(self.hide_overlay_states().get(str(user), {}).get("enabled") == enabled,
                "导航资源配置写入后读回不一致。")

    def settings(self, user, namespace):
        values = {}
        for line in self.shell("settings", "--user", user, "list", namespace).splitlines():
            key, separator, value = line.partition("=")
            if separator and RELEVANT.search(key):
                values[key] = value
        return values

    def read(self, user, namespace, key):
        return state(self.settings(user, namespace), key)

    def write(self, user, namespace, key, value):
        if value["exists"]:
            self.shell("settings", "--user", user, "put", namespace, key, value["value"])
        else:
            self.shell("settings", "--user", user, "delete", namespace, key)
        require(self.read(user, namespace, key) == value, "设置写入后读回不一致，请用备份恢复并检查 OEM 限制。")


def capture(device, label):
    users = device.users()
    personal = int(device.shell("am", "get-current-user"))
    require(str(personal) in users and not users[str(personal)]["managed"], "前台用户不是个人空间。")
    settings = {}
    overlays = {}
    read_errors = {}
    for user in users.values():
        if user["id"] != personal and not user["managed"]:
            continue
        uid = str(user["id"])
        settings[uid] = {}
        for ns in ("secure", "system"):
            try:
                settings[uid][ns] = device.settings(uid, ns)
            except ValueError as error:
                # 受策略限制时保留可读的诊断数据；缺失数据不能生成写入计划。
                settings[uid][ns] = None
                read_errors[uid + "/" + ns] = str(error).splitlines()[0]
        try:
            overlays[uid] = [line for line in device.shell("cmd", "overlay", "list", "--user", uid).splitlines()
                             if re.search(r"nav|gesture|pill", line, re.I)]
        except ValueError as error:
            overlays[uid] = ["读取失败：" + str(error)]
    try:
        hide_overlay = device.hide_overlay_states()
    except ValueError as error:
        hide_overlay = {}
        read_errors["hide_overlay"] = str(error).splitlines()[0]
    return dict(format=1, captured=datetime.now(timezone.utc).isoformat(), state=label,
                device=device.identity(), personal_user=personal, users=users, settings=settings,
                global_settings=device.settings(personal, "global"), navigation_overlays=overlays,
                hide_overlay=hide_overlay, read_errors=read_errors)


def parse_hide_overlays(text):
    """读取 Moto 已有系统资源包的状态；不猜测普通 Settings 键名。"""
    result = {}
    pattern = r"(?m)^" + re.escape(HIDE_OVERLAY) + r":(\d+) \{\n(.*?)^\}"
    for match in re.finditer(pattern, text, re.M | re.S):
        block = match[2]
        def field(name):
            found = re.search(r"(?m)^\s*" + re.escape(name) + r"\.*:\s*(\S+)", block)
            require(found is not None, "无法读取导航资源配置字段：" + name)
            return found[1]
        require(field("mTargetPackageName") == "android"
                and field("mCategory") == "com.android.internal.navigation_bar_mode_hide"
                and field("mIsMutable") == "true", "系统导航资源包与已核实的 Moto 配置不一致。")
        status = field("mState")
        require(status in ("STATE_ENABLED", "STATE_DISABLED"), "系统导航资源包不可用：" + status)
        result[match[1]] = dict(package=HIDE_OVERLAY, enabled=status == "STATE_ENABLED")
    return result


def make_overlay_plan(device, work_user=None):
    identity = device.identity()
    require(identity["manufacturer"].lower() == "motorola", "这个资源配置仅针对已核实的 Moto 实现。")
    personal = int(device.shell("am", "get-current-user"))
    profiles = [user for user in device.users().values() if user["managed"] and user["parent"] == personal]
    if work_user is not None:
        profiles = [user for user in profiles if user["id"] == work_user]
    require(len(profiles) == 1, "需要当前主空间下唯一的工作空间；多个时用 --work-user 指定。")
    work = profiles[0]
    states = device.hide_overlay_states()
    require(states.get(str(personal), {}).get("enabled") is True, "个人空间尚未开启隐藏导航栏。")
    require(states.get(str(work["id"]), {}).get("enabled") is False, "工作空间没有可同步的关闭状态。")
    return dict(format=1, kind="moto-hide-overlay-plan", device=identity, personal_user=personal,
                work_user=work, overlay=HIDE_OVERLAY, before=False, after=True,
                debugging_blocked_at_capture=work.get("debugging_blocked", False))


def validate_overlay_plan(device, plan):
    require(plan.get("format") == 1 and plan.get("kind") == "moto-hide-overlay-plan"
            and plan.get("overlay") == HIDE_OVERLAY and plan.get("before") is False
            and plan.get("after") is True, "不是已核实的 Moto 横条隐藏配置。")
    require(device.identity() == plan["device"], "设备或系统版本已改变。")
    work = plan["work_user"]
    current = device.users().get(str(work["id"]))
    require(current is not None and current["managed"] and current["parent"] == plan["personal_user"]
            and current["id"] != plan["personal_user"] and current["serial"] == work["serial"], "工作空间身份已变化。")
    require(not current.get("debugging_blocked", False),
            "Shelter 工作空间仍禁止调试（no_debugging_features）。请使用空间内系统设置入口；工具不会修改管理策略。")


def apply_overlay(device, plan, backup):
    validate_overlay_plan(device, plan)
    states = device.hide_overlay_states()
    uid = plan["work_user"]["id"]
    require(states.get(str(plan["personal_user"]), {}).get("enabled") is True
            and states.get(str(uid), {}).get("enabled") is plan["before"], "导航资源配置已变化，请重新采集。")
    save(backup, plan)
    device.write_hide_overlay(uid, plan["after"])
    print("工作空间隐藏配置已启用；请重新打开工作应用键盘验证显示效果。")


def restore_overlay(device, plan):
    validate_overlay_plan(device, plan)
    uid = plan["work_user"]["id"]
    current = device.hide_overlay_states().get(str(uid), {}).get("enabled")
    require(isinstance(current, bool), "无法读取当前工作空间的导航配置。")
    if current != plan["before"]:
        device.write_hide_overlay(uid, plan["before"])
    print("已恢复工作空间原有导航资源配置。")


def differences(shown, hidden):
    require(shown.get("state") == "shown" and hidden.get("state") == "hidden", "需要分别标记 shown、hidden 的两份快照。")
    require(shown["device"] == hidden["device"] and shown["personal_user"] == hidden["personal_user"], "两份快照必须来自同一设备、系统版本和个人空间。")
    require(shown["users"] == hidden["users"], "两次采集之间用户/工作空间发生变化，请重新采集。")
    uid = str(hidden["personal_user"])
    changes = []
    for namespace in ("secure", "system", "global"):
        left = shown["global_settings"] if namespace == "global" else shown["settings"][uid][namespace]
        right = hidden["global_settings"] if namespace == "global" else hidden["settings"][uid][namespace]
        require(isinstance(left, dict) and isinstance(right, dict), "个人空间配置读取被系统策略阻止。")
        for key in sorted(left.keys() | right.keys()):
            before, after = state(left, key), state(right, key)
            if before != after:
                eligible = namespace != "global" and bool(VISIBILITY.search(key)) and valid_state(before) and valid_state(after)
                changes.append(dict(namespace=namespace, key=key, shown=before, hidden=after, eligible=eligible))
    return changes


def make_plan(shown, hidden, work_user=None):
    candidates = [item for item in differences(shown, hidden) if item["eligible"]]
    require(len(candidates) == 1, "未找到唯一的按用户保存的显隐开关；请先查看 diff 结果，不能猜测设置名或全局强制隐藏。")
    managed = [user for user in hidden["users"].values() if user["managed"]]
    if work_user is not None:
        managed = [user for user in managed if user["id"] == work_user]
    require(len(managed) == 1, "需要唯一的工作空间；多个时用 --work-user 指定。")
    user = managed[0]
    require(user["id"] != hidden["personal_user"] and user["serial"] is not None, "工作空间身份不完整。")
    require(user["parent"] in (None, hidden["personal_user"]), "工作空间不属于当前个人空间。")
    choice = candidates[0]
    values = hidden["settings"][str(user["id"])][choice["namespace"]]
    require(isinstance(values, dict), "工作空间配置读取被系统策略阻止，不能生成写入计划。")
    old = state(values, choice["key"])
    require(valid_state(old), "工作空间原值不是布尔开关，需人工核查。")
    require(old != choice["hidden"], "工作空间已是相同值；同步设置不能解决此现象，需继续查 System UI / 输入法。")
    return dict(format=1, kind="navigation-visibility-plan", device=hidden["device"],
                personal_user=hidden["personal_user"], work_user=user, namespace=choice["namespace"], key=choice["key"],
                before=old, after=choice["hidden"], source_shown=choice["shown"], source_hidden=choice["hidden"])


def validate_plan(device, plan):
    require(plan.get("format") == 1 and plan.get("kind") == "navigation-visibility-plan", "不支持的配置文件。")
    require(plan["namespace"] in ("secure", "system") and re.fullmatch(r"[a-zA-Z0-9_]+", plan["key"])
            and VISIBILITY.search(plan["key"]), "仅允许按用户保存的导航显隐开关。")
    require(all(valid_state(plan[key]) for key in ("before", "after", "source_shown", "source_hidden")), "配置值不是布尔开关。")
    require(plan["source_shown"] != plan["source_hidden"] and plan["after"] == plan["source_hidden"], "配置缺少显示/隐藏状态差异。")
    require(device.identity() == plan["device"], "设备或系统版本已改变；请重新采集。")
    users = device.users()
    user = plan["work_user"]
    require(user["id"] != plan["personal_user"] and user["managed"] and user["serial"] is not None,
            "目标必须是工作空间。")
    require(users.get(str(user["id"])) == user, "工作空间已被删除、重建或改变；拒绝使用旧备份。")


def apply_plan(device, plan, backup):
    validate_plan(device, plan)
    uid, ns, key = plan["work_user"]["id"], plan["namespace"], plan["key"]
    require(device.read(plan["personal_user"], ns, key) == plan["source_hidden"], "个人空间已不是采集时的隐藏状态。")
    require(device.read(uid, ns, key) == plan["before"], "工作空间配置已变化，请重新采集。")
    save(backup, plan)
    print("已保存恢复记录：", backup, flush=True)
    device.write(uid, ns, key, plan["after"])
    print("已写入并读回确认。请重新打开工作空间应用的输入框，检查横条；写入成功不代表 Moto 界面一定采用此值。")


def restore(device, plan):
    validate_plan(device, plan)
    uid, ns, key = plan["work_user"]["id"], plan["namespace"], plan["key"]
    current = device.read(uid, ns, key)
    if current == plan["before"]:
        print("已经是原始配置。")
        return
    require(current == plan["after"], "配置后来被其他操作更改；不会用旧备份覆盖，请重新核查。")
    device.write(uid, ns, key, plan["before"])
    print("已恢复原始配置。原本不存在的设置项已删除。")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb")
    parser.add_argument("--serial", help="ADB 设备序列号；默认仅选择唯一的已授权实体手机")
    commands = parser.add_subparsers(dest="command", required=True)
    snapshot = commands.add_parser("capture", help="只读采集导航相关配置")
    snapshot.add_argument("--state", choices=("shown", "hidden", "current"), default="current")
    snapshot.add_argument("--output", type=Path, required=True)
    for name in ("diff", "plan"):
        command = commands.add_parser(name, help="比较主空间显示/隐藏横条时的实际差异")
        command.add_argument("--shown", type=Path, required=True)
        command.add_argument("--hidden", type=Path, required=True)
        if name == "plan":
            command.add_argument("--work-user", type=int)
            command.add_argument("--output", type=Path, required=True)
    command = commands.add_parser("apply", help="仅修改计划中的工作空间显隐开关")
    command.add_argument("--plan", type=Path, required=True)
    command.add_argument("--backup", type=Path, required=True)
    command = commands.add_parser("restore", help="从恢复记录还原该开关")
    command.add_argument("--backup", type=Path, required=True)
    command = commands.add_parser("overlay-plan", help="为已核实的 Moto hidegestural 系统资源包生成计划，不写设备")
    command.add_argument("--work-user", type=int)
    command.add_argument("--output", type=Path, required=True)
    command = commands.add_parser("apply-overlay", help="应用 Moto 资源配置；调试受限时拒绝执行")
    command.add_argument("--plan", type=Path, required=True)
    command.add_argument("--backup", type=Path, required=True)
    command = commands.add_parser("restore-overlay", help="恢复 Moto 资源配置")
    command.add_argument("--backup", type=Path, required=True)
    args = parser.parse_args()
    if args.command in ("diff", "plan"):
        shown, hidden = load(args.shown), load(args.hidden)
        if args.command == "diff":
            print(json.dumps(differences(shown, hidden), ensure_ascii=False, indent=2))
        else:
            result = make_plan(shown, hidden, args.work_user)
            save(args.output, result)
            print(json.dumps(result, ensure_ascii=False, indent=2))
        return
    device = Device(args.adb, args.serial)
    if args.command == "capture":
        result = capture(device, args.state)
        save(args.output, result)
        print(json.dumps(dict(device=result["device"], users=result["users"], read_errors=result["read_errors"], saved=str(args.output)), ensure_ascii=False, indent=2))
    elif args.command == "apply":
        apply_plan(device, load(args.plan), args.backup)
    elif args.command == "restore":
        restore(device, load(args.backup))
    elif args.command == "overlay-plan":
        plan = make_overlay_plan(device, args.work_user)
        save(args.output, plan)
        print(json.dumps(plan, ensure_ascii=False, indent=2))
    elif args.command == "apply-overlay":
        apply_overlay(device, load(args.plan), args.backup)
    else:
        restore_overlay(device, load(args.backup))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError, subprocess.SubprocessError) as error:
        raise SystemExit(str(error))
