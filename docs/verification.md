# 0.1.0 测试记录

日期：2026-10-31。交付为个人试用的 **ARM64 Debug APK**，包名 `indi.tatenlee.inputmethod`，最低 Android 9。测试环境是本机隔离的 Android 15 / API 35 ARM64 模拟器，1080 × 2400、420 dpi、3 GB 内存、4 KB 内核页。没有连接或修改实体手机。

## 已完成

| 检查 | 结果与证据 |
| --- | --- |
| Gradle 构建、核心单元测试、Android Lint | 构建成功；5 项单元测试通过；Lint 0 错误、10 警告。[单元测试](evidence/core-tests.xml)、[Lint 明细](evidence/android-lint.txt) |
| 实际 RIME 核心回放 | 全拼 `nihao / zhongguo / shurufa`、简拼 `nh`、九键 `64426 / 94664486`，6 项通过。[回放记录](evidence/rime-host-replay.txt) |
| Android JNI、词库和语音模型 | 2 项集成测试通过：全拼 / 九键选择「你好」，以及打包后的模型识别公开中文音频。[记录](evidence/android-integration-tests.txt) |
| 清空测试应用数据后的首次部署 | 同样 2 项测试通过，包含资源复制、字典部署和语音识别，共 9.437 秒。该时间只代表本次模拟器运行。[首次部署记录](evidence/android-integration-tests-cold.txt) |
| 原生兼容与安装 | 3 个 `.so` 全部 ELF64 / AArch64；依赖仅指向随包 libc++ 与 Android 系统库；ELF LOAD 和 APK ZIP 均满足 16 KB 对齐；zipalign 和 APK v2 签名验证通过；成功安装。[APK 审计](evidence/jianxin-apk-audit.json)、[签名](evidence/jianxin-signature.txt) |
| 权限 | 安装包仅申请麦克风权限，没有 INTERNET 权限。[Manifest](evidence/jianxin-manifest.txt) |
| 导航区 | 手势横条、三键导航下检查完整底行；在 Android 系统设置搜索框中实际输入并提交「你好」 |
| 底部额外距离 | 从 8 dp 调到 32 dp，系统区域保持 126 px，键盘总高度增加 63 px，符合 24 × 2.625 的预期。[测量](evidence/extra-bottom-check.json)、[截图](evidence/extra-bottom-32dp.png) |
| 工具栏排序 | 在设置界面将剪贴板移到第一位；读取持久化配置并重启进程，界面仍保持顺序。[竖屏截图](evidence/keyboard-portrait.png) |
| 横竖屏 | 连续 10 次切换，检查当前窗口、键盘尺寸和底部区域，没有沿用上一方向的宽高。[测量记录](evidence/rotation-check.json)、[横屏截图](evidence/keyboard-landscape.png) |
| 录音生命周期 | 点击语音进入录音状态，旋转时停止麦克风，未把取消结果写入输入框。系统 AppOps 显示录音已结束。[录音界面](evidence/voice-recording.png)、[停止记录](evidence/voice-after-rotation.txt) |

语音样本的实际输出为「开饭时间早上9点至下午5点。」；官方示例期望「开放时间早上9点至下午5点。」。“开放”识别成“开饭”，所以该测试证明离线链路可运行、时间信息可识别，**不代表逐字正确或已经完成准确率验收**。样本来源与校验值在 `app/src/androidTest/assets/README.md`，参照 [sherpa-onnx 官方示例](https://k2-fsa.github.io/sherpa/onnx/sense-voice/pretrained.html)。

Lint 警告涉及目标 SDK、固定依赖版本、未提供 ChromeOS x86 ABI、备份配置提示、仅代码创建的视图构造器和中文界面未国际化。没有用忽略全部告警的配置掩盖它们。

## 仍需你的手机验证

- Moto 的具体型号、Android 版本、工作空间策略，以及横条常驻时的实际安全区域。
- Android 16 与真实 16 KB 页设备：本次仅做了 16 KB 静态对齐检查，运行环境为 Android 15 / 4 KB。
- 日常全拼 / 九键候选排序、长句、错拼、模糊音、口音和嘈杂环境语音。当前九键是基础映射，没有 CyIME 的完整增强功能。
- 视频应用、聊天应用、分屏、多显示器和不同字号。宿主应用重建输入框时，尚未提交的拼音可能结束组合状态；本版不保证跨宿主重建恢复组合输入。
- 真机的长时间耗电、录音识别延迟、内存和稳定性。

原搜狗 APK 未改写。此工程提供自己的应用入口、代码和可重复准备的依赖，不依赖搜狗的 32 位二进制。
