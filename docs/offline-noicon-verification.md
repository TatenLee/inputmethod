# 0.1.1 离线与无桌面入口验证

验证日期：2026-11-01。交付包：`dist/jianxin-0.1.1-arm64-offline-noicon-debug.apk`，284,181,298 字节，包名 `indi.tatenlee.inputmethod`，versionCode 2。SHA-256：

```text
1f080625f2e45454084fd36bd08e91d2c2ca1cc894766e715e3ef66eeee34751
```

与 0.1.0 使用同一调试签名，模拟器上覆盖安装成功；本次未修改原始搜狗 APK。

## 结论与证据

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 最终 APK 权限 | 仅 `RECORD_AUDIO`；无 `INTERNET`、网络状态、Wi-Fi 权限或共享 UID | [APK 审计](evidence/jianxin-0.1.1-offline-audit.json)、[权限](evidence/jianxin-0.1.1-permissions.txt) |
| DEX 常见网络入口 | 无 URL 读取、Socket、HTTP 客户端、WebView、下载器、系统远程语音等被扫描的 API 引用 | 同上；检查器的扫描范围在源码中列明 |
| 原生库网络导入 | 三个 ARM64 库均未发现被扫描的网络函数导入 | 同上 |
| Android 系统权限隔离 | 应用 UID 10148 的 IPv4/IPv6 × TCP/UDP 套接字创建均被拒绝，errno=1（EPERM） | [运行日志](evidence/offline-policy-runtime.txt) |
| 桌面与应用抽屉 | 无 Launcher intent-filter；包管理器和 `LauncherApps.getActivityList` 都返回空入口列表 | [6 项集成测试](evidence/offline-policy-tests.txt)、[Manifest](evidence/jianxin-0.1.1-manifest.txt) |
| 设置入口 | 输入法元数据仍指向设置 Activity，实际从系统输入法列表点开成功 | 同上、[设置页截图](evidence/noicon-settings-entry.png) |
| 断网首次使用 | 飞行模式开启，Wi-Fi/移动数据关闭，清除测试应用数据后首次部署 RIME、全拼/九键“你好”上屏及本地语音识别通过 | [2 项冷启动测试](evidence/offline-native-tests.txt)、[网络开关状态](evidence/offline-device-state.json) |
| ARM64 / 16 KB | 三个原生库的 ELF 和 ZIP 对齐通过；签名验证、zipalign 通过 | [APK 审计](evidence/jianxin-0.1.1-offline-audit.json)、[签名](evidence/jianxin-0.1.1-signature.txt) |

上述测试环境为 Android 15 / API 35 ARM64 模拟器。相同 SHA-256 的 0.1.1 APK 已安装到 Moto X40 / Android 16 的个人和工作空间，设置页冷启动成功；中文、语音与旋转的实机体验仍由用户测试，未在 Moto 重跑上述整套测试。网络拒绝测试使用输入法自己的 UID，不借用 shell 权限，不向外部地址发送数据；随后才关闭模拟器网络进行冷启动功能测试。

Android 将 `INTERNET` 定义为允许应用打开网络套接字的权限；简心没有申请此权限，运行验证也确认系统拒绝创建 IP 套接字。[Android 权限定义](https://developer.android.com/reference/android/Manifest.permission#INTERNET)

## 改动说明

0.1.0 已没有联网权限和业务联网功能，但仍包含 Kotlin 标准库中未使用的 `URL.openStream` 辅助方法，也提供桌面入口。0.1.1 开启依赖裁剪，保留可读的应用类名和 JNI 必需接口，移除未使用的网络读取方法。源码检查未发现上传、埋点、更新检查、浏览器跳转或远程语音调用。语音继续使用内置 SenseVoice，词库和模型都随 APK 分发。

静态 API 和导入扫描不是对任意隐藏代码的形式化证明；这里的结论同时基于可读源码、最终权限、依赖扫描和 Android 权限执行结果。扫描器用旧版 0.1.0 作为负例，能检出其 Launcher 入口和 URL 辅助方法。

设置 Activity 完全移除了 `MAIN/LAUNCHER` intent-filter，没有采用“安装后动态禁用图标”的方式。Android 的 LauncherApps 可能为某些无入口应用合成应用详情入口，因此测试也检查了这个 API，而不只看 Manifest。[LauncherApps 说明](https://developer.android.com/reference/android/content/pm/LauncherApps#getActivityList(java.lang.String,android.os.UserHandle))

“无图标”指没有桌面和应用抽屉启动项。系统应用管理、权限页和输入法列表仍应显示应用身份；其中的名称、图标用于识别已安装的输入法。设置页和许可证页也声明了不进入最近任务列表。

## 后续构建约定

Gradle 打包前检查合并后的 Manifest，依赖若带入其他权限、Launcher 或共享 UID，直接构建失败。发布前继续执行：

```sh
scripts/build.sh :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
python3 scripts/check-apk.py app/build/outputs/apk/debug/app-debug.apk
```

电脑准备构建环境时下载依赖，与安装到手机后的运行能力分开。配置 Moto 工作空间的 ADB 工具也不会打包到输入法里。
