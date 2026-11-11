#!/usr/bin/env python3
"""打包两个独立测试 APK、激活脚本及只包含这两个工具的可构建源码。"""
from pathlib import Path
import hashlib
import json
import shutil
import zipfile

ROOT = Path(__file__).resolve().parent.parent
DIST = ROOT / "dist"
VERSION = "0.2.0"
BUNDLE = DIST / f"jianxin-tools-{VERSION}-test"
SOURCE = BUNDLE / "source"


def copy(src, dst):
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dst)


def main():
    BUNDLE.mkdir(parents=True, exist_ok=True)
    checksums = {}
    for module in ("profile-control", "sms-code"):
        apk = ROOT / module / "build/outputs/apk/debug" / f"{module}-debug.apk"
        metadata = json.loads((apk.parent / "output-metadata.json").read_text())
        module_version = metadata["elements"][0]["versionName"]
        name = f"{module}-{module_version}-offline-debug.apk"
        copy(apk, DIST / name)
        copy(apk, BUNDLE / name)
        checksums[name] = hashlib.sha256(apk.read_bytes()).hexdigest()
        copy(ROOT / module / "build.gradle.kts", SOURCE / module / "build.gradle.kts")
        for p in (ROOT / module / "src").rglob("*"):
            if p.is_file() and p.name != ".DS_Store": copy(p, SOURCE / p.relative_to(ROOT))
    for p in (ROOT / "tool-tests/src").rglob("*"):
        if p.is_file(): copy(p, SOURCE / p.relative_to(ROOT))
    for name in ("LICENSE", "gradlew", "gradlew.bat", "gradle.properties",
                 "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties",
                 "scripts/run-gradle.py", "scripts/activate-profile-control.py", "scripts/test-offline-tools.py"):
        copy(ROOT / name, SOURCE / name)
    copy(ROOT / "scripts/activate-profile-control.py", BUNDLE / "activate-profile-control.py")
    (SOURCE / "settings.gradle.kts").write_text('''pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "JianxinTools"
include(":profile-control", ":sms-code")
''')
    (SOURCE / "build.gradle.kts").write_text('plugins { id("com.android.application") version "8.11.1" apply false }\n')
    (SOURCE / ".gitignore").write_text(".gradle/\n**/build/\nlocal.properties\n*.jks\n*.keystore\n.idea/\n")
    guide = (ROOT / "docs/offline-tools.md").read_text().replace(
        "[原有 Moto 维护说明](moto-work-profile.md)", "原工程 docs/moto-work-profile.md（旧命令不随此工具包发布）")
    (BUNDLE / "使用说明.md").write_text(guide)
    (SOURCE / "README.md").write_text(guide)
    (BUNDLE / "README.txt").write_text(f"简心独立工具 {VERSION} 测试包\n\n先阅读 使用说明.md。\n"
        "工作空间工具：电脑运行 python3 activate-profile-control.py，会安装并激活。\n"
        "验证码工具：安装 sms-code APK，点击允许接收新短信；需要辅助删除再开启无障碍。\n"
        "source/ 是独立 Gradle 工程，使用 JDK 17、SDK 36 和 Build Tools 36.0.0。\n"
        "两个工具均无联网权限；工作应用权限使用辅助入口，原来 Moto 的空白入口未被修复。\n"
        "AOSP 单条删除已通过模拟器测试，Moto 短信界面与新横条 APK 的实机切换仍需验收。\n")
    (BUNDLE / "SHA256SUMS.txt").write_text("".join(f"{value}  {name}\n" for name, value in checksums.items()))
    archive = DIST / f"jianxin-tools-{VERSION}-test.zip"
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as z:
        for p in sorted(BUNDLE.rglob("*")):
            relative = p.relative_to(BUNDLE)
            if p.is_file() and not any(part in {"build", ".gradle", "__pycache__", "local.properties"} for part in relative.parts):
                z.write(p, relative)
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None
    print(json.dumps({"archive":str(archive),"bytes":archive.stat().st_size,
        "sha256":hashlib.sha256(archive.read_bytes()).hexdigest(),"apks":checksums},ensure_ascii=False,indent=2))


if __name__ == "__main__": main()
