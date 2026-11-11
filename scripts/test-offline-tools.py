#!/usr/bin/env python3
"""只在 Android 模拟器上验证离线隔离及新短信后台复制；不会操作实体手机。"""
import argparse
from pathlib import Path
import secrets
import shutil
import subprocess
import time

ROOT=Path(__file__).resolve().parent.parent


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial",default="emulator-5554")
    parser.add_argument("--adb")
    args=parser.parse_args()
    candidate=ROOT/".toolchain/android-sdk/platform-tools/adb"
    adb=args.adb or (str(candidate) if candidate.exists() else shutil.which("adb"))
    if not adb:raise SystemExit("未找到 adb")
    base=[adb,"-s",args.serial]
    def run(*parts):return subprocess.check_output(base+list(parts),text=True,timeout=50)
    if not args.serial.startswith("emulator-") or run("shell","getprop","ro.kernel.qemu").strip()!="1":
        raise SystemExit("此脚本只允许模拟器")
    if run("shell","am","get-current-user").strip()!="0":raise SystemExit("测试要求模拟器当前用户为 0")
    evidence=ROOT/"docs/evidence";evidence.mkdir(parents=True,exist_ok=True)
    before_services=run("shell","settings","get","secure","enabled_accessibility_services").strip()
    before_enabled=run("shell","settings","get","secure","accessibility_enabled").strip()
    before_airplane=run("shell","settings","get","global","airplane_mode_on").strip()
    def instrument(package,classes,expected,extra=()):
        output=run("shell","am","instrument","-w","-r","-e","class",classes,*extra,package+".test/androidx.test.runner.AndroidJUnitRunner")
        if expected not in output:raise RuntimeError(output)
        return output
    try:
        if before_airplane=="1":
            run("shell","cmd","connectivity","airplane-mode","disable");time.sleep(2)
        # 测试新短信复制时明确关闭无障碍，排除该路径帮忙复制的可能。
        run("shell","settings","delete","secure","enabled_accessibility_services")
        run("shell","settings","put","secure","accessibility_enabled","0")
        for module,package,classes,count in [
                ("profile-control","indi.taten.workprofile","indi.taten.tools.OfflinePolicyTest",2),
                ("sms-code","indi.taten.smscode","indi.taten.smscode.SmsBehaviourTest,indi.taten.tools.OfflinePolicyTest",6)]:
            for apk in [ROOT/module/f"build/outputs/apk/debug/{module}-debug.apk",
                        ROOT/module/f"build/outputs/apk/androidTest/debug/{module}-debug-androidTest.apk"]:
                run("install","--user","0","-r","-g",str(apk))
            result=instrument(package,classes,f"OK ({count} tests)")
            (evidence/f"{module}-offline-tests.txt").write_text(result)
            print(module,f"{count} tests passed",flush=True)
        run("shell","am","start","-n","indi.taten.smscode/.MainActivity");time.sleep(.5)
        run("shell","input","keyevent","KEYCODE_HOME");time.sleep(.4)
        code=str(secrets.randbelow(900000)+100000)
        run("emu","sms","send","10690002",f"Your verification code is {code}. JIANXIN_LOCAL_TEST.");time.sleep(1)
        run("emu","sms","send","10690002","JIANXIN_LOCAL_TEST: Ordinary receipt 998877 must not replace the code.");time.sleep(.7)
        result=instrument("indi.taten.smscode","indi.taten.smscode.SmsReceiverDeviceTest","OK (1 test)",("-e","expected_sms_code",code))
        (evidence/"sms-background-receiver-test.txt").write_text("Accessibility disabled; app in background; OTP followed by ordinary SMS.\n"+result)
        print("Background SMS copy passed; ordinary SMS did not replace it.",flush=True)
    finally:
        for key,value in [("enabled_accessibility_services",before_services),("accessibility_enabled",before_enabled)]:
            if value in ("null",""):run("shell","settings","delete","secure",key)
            else:run("shell","settings","put","secure",key,value)
        if before_airplane=="1":run("shell","cmd","connectivity","airplane-mode","enable")


if __name__=="__main__":main()
