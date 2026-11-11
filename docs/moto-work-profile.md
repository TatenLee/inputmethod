# Moto X40 工作空间横条：实机修复与备份

验证日期：2026-11-01。设备为 Moto X40（XT2301-5）、Android 16，Shelter 工作空间 user 12 / serial 12，个人空间 user 0。工作空间的小白条已修复，用户在重新打开键盘后确认「已消失」。

## 实际修改

Moto 使用系统资源覆盖包 `com.android.internal.systemui.navbar.hidegestural` 隐藏手势横条，按用户分别保存启用状态。修复前主空间启用、工作空间禁用；此次只将工作空间的该包启用，没有改变三键/手势导航模式。

| 项目 | 修复前 | 修复后 |
| --- | --- | --- |
| 个人空间隐藏横条 | 开启 | 开启，保持不变 |
| 工作空间隐藏横条 | 关闭 | 开启，系统读回成功，用户确认消失 |
| 工作空间 AOSP 键盘 | 已安装，Shelter 冻结未生效 | `installed=false`，仅在工作空间卸载 |
| 个人空间 AOSP 键盘 | 已安装 | 首次维护保留；用户后续明确要求卸载后，已确认 `installed=false` |
| 语燕输入法 | 两个空间均已安装，默认使用 | 保持安装和默认设置 |
| 简心输入法 0.1.1 | 尚未安装 | 已完成两个空间的安装验证；由用户启用、切换测试 |

首次工作空间的 AOSP 卸载采用 `DELETE_SYSTEM_APP | DELETE_KEEP_DATA`，没有 `DELETE_ALL_USERS`，当时保留了系统分区和个人空间副本。结果见 [实机记录](evidence/moto-work-profile-result.json)。这里没有把输入法底部留白当作系统横条隐藏。

用户随后明确要求移除主空间的 AOSP 键盘。普通 `pm uninstall -k --user 0` 被 Moto 拒绝，提示 `package is non-disable`；因此增加了单独的 `remove-main-aosp` 测试动作，仅接受显式的 user 0 / serial 0。通过 `PackageInstaller` 对 user 0 执行相同的单用户卸载后返回 `DELETE_SUCCEEDED`。读回确认主空间 `installed=false`，语燕和搜狗保留、默认仍为语燕，其他用户及导航 overlay 状态保持不变。系统分区中的 AOSP APK 和数据仍保留。此次临时装回的辅助 App 和测试 APK 都已从主空间清理。[主空间卸载记录](evidence/moto-main-aosp-removal.json)

新增动作实机验证了两个拒绝条件：主空间卸载不能指定 user 12；工作空间卸载不能指定 user 0。二者均在修改前拒绝。[边界测试](evidence/moto-main-aosp-guard-tests.txt) 原横条备份 ZIP 保留此前仅操作工作空间的组件，新增主空间卸载动作不会改变已有备份命令的用途。

## 为什么普通 ADB 不行，这次却成功

工作空间的 `no_debugging_features` 限制由 Shelter 管理。这台手机的普通 `adb shell ... --user 12` 会被系统拒绝；主空间打开 USB 调试不等于解除工作空间策略。Shelter 当前没有解除该项限制的入口。[Android 用户限制定义](https://developer.android.com/reference/android/os/UserManager#DISALLOW_DEBUGGING_FEATURES)

这次使用的是独立的 Android instrumentation 测试 APK，运行在主空间的「简心·空间设置」进程中。Android 的 `UiAutomation.adoptShellPermissionIdentity` 允许在电脑调试会话期间临时使用明确列出的 shell 权限；进程仍保留自己的应用 UID。[Android UiAutomation](https://developer.android.com/reference/android/app/UiAutomation#adoptShellPermissionIdentity(java.lang.String...))

实际流程为：

1. 在电脑备份设备身份、工作空间编号及 serial、主空间和工作空间的原状态。
2. 在主空间运行一次性 shell 小程序，把 `overlay` 系统服务的 Binder 引用交给自己的测试组件。交接 provider 要求 `DUMP` 权限，写入只接受 shell UID，读取只接受本工具 UID，并且取出后清空。
3. 测试进程临时采用跨用户与 `CHANGE_OVERLAY_PACKAGES` 权限，核对 Moto 型号、Android 版本、关联工作空间和原状态，然后调用 `IOverlayManager.setEnabled(包名, true, 12)`。
4. 读回确认工作空间已开启、其他空间不变。测试进程在 `finally` 撤销权限，完成维护后删除测试 APK。

该操作没有 Root，没有清除 Shelter 的管理权限，没有删除或重建工作空间，也没有永久关闭 Android 的隐藏 API 检查。`--no-hidden-api-checks` 仅用于本工具的 instrumentation 进程。普通 ADB 对工作空间的限制仍然存在。测试代码不包含在简心输入法 APK 中。

## 可备份和重复执行的命令

脚本：[scripts/moto-work-profile-control.py](../scripts/moto-work-profile-control.py)。备份目录：`dist/moto-work-profile-backup-20261101/`，包含两个工具 APK、脚本及修改前的 `restore.json`。该记录包含本机 ADB 标识与系统版本，用于拒绝误用于另一台手机。

在项目根目录执行：

```sh
# 查看当前状态，不修改手机
python3 scripts/moto-work-profile-control.py inspect

# 隐藏工作空间横条；已隐藏时不会重复修改
python3 scripts/moto-work-profile-control.py hide --backup dist/moto-work-profile-backup-20261101/restore.json

# 恢复修改前的显示状态，仅影响工作空间
python3 scripts/moto-work-profile-control.py restore --backup dist/moto-work-profile-backup-20261101/restore.json
```

手机需要保持 USB 调试授权和工作空间开启。脚本自动选择唯一已授权的实体手机；多设备时加 `--serial 设备序列号`。脚本查找项目内 ADB、PATH 或 macOS Android SDK，也可显式添加 `--adb /ADB的路径`。

将备份 ZIP 解压到其他目录后，在解压目录执行同样的脚本名，并把 `--backup` 改成 `restore.json`。完整目录已经带上工具 APK，不需要重新编译；ADB 仍需可用。源代码构建命令：

```sh
python3 scripts/run-gradle.py :work-profile-helper:assembleDebug :work-profile-helper:assembleDebugAndroidTest
```

脚本核对手机、系统版本、工作空间 serial、原状态，先落盘备份再写入；完成后清理临时测试 APK。恢复使用相同服务将该包在 user 12 下禁用。为保留当前已经修好的状态，本次没有在手机上往返切换验证恢复命令；恢复路径已做状态与权限边界检查。

系统会保存 overlay 状态，本次尚未验证重启或系统更新后的保留情况。系统版本或工作空间身份变化时，脚本拒绝套用旧记录，需要重新检查。

## 工程位置与验证范围

- `work-profile-helper/src/main/`：普通辅助 App，只提供设置入口和试打框；没有联网及系统修改权限，作为独立工具有桌面图标。Moto 的工作空间设置页面可能为空或跳回主空间，它不是此次修复的写入机制。
- `work-profile-helper/src/androidTest/`：临时维护组件，仅允许已核实设备的指定动作；日常工具和简心输入法均不打包此代码。
- `scripts/moto-work-profile.py`：旧的只读采集、普通 ADB 比较与备份工具。普通 overlay 写入会在 `no_debugging_features` 存在时拒绝执行。
- `scripts/moto-work-profile-control.py`：本次成功路径的电脑入口，负责备份、限定目标、运行临时组件和清理。
- [14 项工具测试](evidence/work-profile-tool-tests.txt)：覆盖错设备、系统变更、工作空间重建、主空间目标、任意 overlay、备份覆盖、失败返回识别与失败后清理。
- [此前模拟器测试](evidence/work-profile-emulator-test.json)：仅验证通用配置的备份恢复机制；不作为 Moto 横条已修复的证据。

简心输入法本体已安装 0.1.1，权限仍只有麦克风、无联网和桌面启动入口，完整离线测试范围见 [离线与无图标验证](offline-noicon-verification.md)。Moto 上的输入体验交由用户继续测试，不能把安装成功当作中文、语音和旋转体验均已验收。
