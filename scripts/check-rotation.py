#!/usr/bin/env python3
"""在已打开简心键盘的隔离模拟器上反复旋转，检查实际窗口和视图尺寸。"""
import argparse
import json
import re
import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--serial", default="emulator-5554")
parser.add_argument("--output", type=Path, default=ROOT / "docs/evidence/rotation-check.json")
args = parser.parse_args()
if not args.serial.startswith("emulator-"):
    raise SystemExit("此脚本只用于隔离模拟器，不修改实体手机设置。")
adb = [str(ROOT / ".toolchain/android-sdk/platform-tools/adb"), "-s", args.serial]


def shell(*values):
    return subprocess.check_output(adb + ["shell", *values], text=True)


records = []
initial = shell("dumpsys", "input_method")
if "Jianxin layout" not in initial or "mWindowVisible=true" not in initial:
    raise SystemExit("请先点击输入框，确认简心键盘已显示，再运行旋转测试。")
shell("settings", "put", "system", "accelerometer_rotation", "0")
try:
    for rotation in [1, 0] * 5:
        shell("settings", "put", "system", "user_rotation", str(rotation))
        time.sleep(.8)
        deadline = time.monotonic() + 8
        while True:
            time.sleep(.35)
            display = shell("dumpsys", "window", "displays")
            state = shell("dumpsys", "input_method")
            screen = re.search(r"cur=(\d+)x(\d+)", display)
            root = re.search(r"ImeLayout size=(\d+)x(\d+).*paddingBottom=(\d+)", state)
            keyboard = re.search(r"KeyboardView size=(\d+)x(\d+)", state)
            if screen and root and keyboard and "mIsInputViewShown=true" in state and "mWindowVisible=true" in state:
                width, height = map(int, screen.groups())
                rw, rh, bottom = map(int, root.groups())
                kw, kh = map(int, keyboard.groups())
                if (width > height) == bool(rotation) and rw == width and rw == kw and rh == kh + bottom and 0 < rh < height:
                    break
            if time.monotonic() >= deadline:
                raise AssertionError("旋转后布局未稳定，或输入法已收起。")
        records.append(dict(rotation=rotation, screen=[width, height], root=[rw, rh], keyboard=[kw, kh], bottom=bottom))
        print(records[-1], flush=True)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(dict(serial=args.serial, rotations=records, passed=True), indent=2) + "\n")
finally:
    if shell("settings", "get", "system", "user_rotation").strip() != "0":
        shell("settings", "put", "system", "user_rotation", "0")
