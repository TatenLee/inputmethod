#!/usr/bin/env python3
"""启动隔离的 Android 15 ARM64 测试机，不修改实体手机的输入法设置。"""
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SDK = ROOT / ".toolchain/android-sdk"
AVDS = ROOT / ".toolchain/avd"
AVD = AVDS / "Jianxin_API35.avd"
image = SDK / "system-images/android-35/default/arm64-v8a"
if not (image / "system.img").exists():
    raise SystemExit("请先安装 system-images;android-35;default;arm64-v8a 到项目 SDK。")
AVD.mkdir(parents=True, exist_ok=True)
(AVDS / "Jianxin_API35.ini").write_text("avd.ini.encoding=UTF-8\npath=" + str(AVD) + "\ntarget=android-35\n")
(AVD / "config.ini").write_text("""AvdId=Jianxin_API35
avd.ini.displayname=Jianxin Android 15
avd.ini.encoding=UTF-8
abi.type=arm64-v8a
hw.cpu.arch=arm64
hw.cpu.ncore=4
hw.ramSize=3072
hw.lcd.width=1080
hw.lcd.height=2400
hw.lcd.density=420
hw.keyboard=yes
hw.mainKeys=no
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader
disk.dataPartition.size=3G
image.sysdir.1=system-images/android-35/default/arm64-v8a/
tag.id=default
tag.display=Default Android System Image
showDeviceFrame=no
skin.dynamic=yes
skin.name=1080x2400
""")
env = dict(os.environ, ANDROID_HOME=str(SDK), ANDROID_SDK_ROOT=str(SDK), ANDROID_AVD_HOME=str(AVDS))
args = [str(SDK / "emulator/emulator"), "-avd", "Jianxin_API35", "-port", "5554",
        "-no-window", "-no-audio", "-no-snapshot", "-no-boot-anim", "-gpu", "swiftshader"]
os.execve(args[0], args, env)
