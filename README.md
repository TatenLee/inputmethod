# 简心输入法

属于自己的 Android 输入法。名称 **简心输入法**，包名 **`indi.tatenlee.inputmethod`**。应用界面和工程独立维护，底层使用固定版本的 RIME、雾凇词库和 SenseVoice 离线语音。

## 第一版的功能

| 需求 | 0.1.1 的实现 |
| --- | --- |
| 全面屏底部横条 | 根据当前系统导航区域留白，横屏、竖屏各有额外距离和键盘高度设置 |
| 现代 64 位设备 | ARM64 原生构建，最低 Android 9；原生库与 APK 按 16 KB 对齐检查 |
| 表情、剪贴板按钮位置 | 设置中长按拖动或用箭头排序，关闭应用后保留 |
| 语音输入 | 内置 SenseVoice 量化模型，点一次录音，再点一次识别；每段最长 30 秒 |
| 横竖屏切换 | 每次使用当前窗口尺寸重新布局，横屏不进入全屏编辑模式 |
| 中文输入 | 全拼、简拼、基础九键，候选翻页、用户词频；英文、数字、符号和 Emoji 面板 |
| 完全离线 | 只声明麦克风权限；不含联网权限，中文和语音资源随包内置 |
| 安装后无桌面图标 | 不注册桌面或应用抽屉入口；通过系统输入法设置或键盘工具栏进入设置 |

这是一版可以继续打磨的基础实现。九键暂未引入 CyIME 的增强插件、拼音筛选栏与纠错调优；也没有搜狗的云联想、图片表情包、滑行输入或剪贴板历史。输入质量和 Moto 工作空间适配需要在你的手机上继续验收。

应用没有联网权限。麦克风仅在使用语音时申请；录音只在内存中处理。剪贴板只在点击时读取。密码与禁止个性化学习的输入框默认英文，并禁用语音、剪贴板和用户词库学习。

## 工程导航

```text
app/            Android 入口、输入法服务、设置页面、应用名称
keyboard/       按键、候选栏、工具栏和各个面板的绘制
settings/       配置读取、保存和默认值
engine-rime/    RIME 适配、JNI、CMake 和自己的拼音方案
speech/         录音、离线模型加载、识别和取消
core/           与 Android 无关的小规则及单元测试
third_party/    版本锁定和许可证；下载的依赖源码不混入应用代码
scripts/        环境准备、资源下载、构建、APK 检查
docs/           开发说明、验证记录和早期调研
```

从 [开发指南](docs/development.md) 了解按键如何变成文字、修改每个功能应该打开哪个文件，以及以后怎样吸收 CyIME / RIME 更新。

最新测试包在 [dist/jianxin-0.1.1-arm64-offline-noicon-debug.apk](dist/jianxin-0.1.1-arm64-offline-noicon-debug.apk)，约 271 MiB（内置离线语音模型），可覆盖安装此前的 0.1.0 测试包。[离线与无图标验证](docs/offline-noicon-verification.md)记录了权限、系统拒绝网络套接字、断网首次部署和设置入口测试；原有键盘布局检查见 [0.1.0 测试记录](docs/verification.md)。

Moto X40 / Android 16 / Shelter 工作空间的系统横条已通过 [独立维护工具](docs/moto-work-profile.md)修复，用户实机确认消失；按用户先后的请求，工作空间和主空间的 AOSP 键盘均已分别卸载，默认语燕保持不变。简心 0.1.1 已完成两个空间的安装验证，输入体验由用户继续验收。文档包含横条配置的备份与恢复命令；该维护组件不打包进输入法。

## 构建

另有两个独立的离线工具：**简心·工作空间**（ADB 激活后控制兼容机型的工作空间横条）和 **简心验证码**（包名 `indi.taten.smscode`，保留原短信 App，接收新短信后离线复制；无障碍用于页面识别和辅助删除）。使用方法、源码结构和尚未通过的实机验收项见 [独立工具说明](docs/offline-tools.md)。它们不依赖输入法，可单独构建。

在当前 Apple Silicon Mac 上首次准备：

```sh
python3 scripts/bootstrap-macos.py
python3 scripts/prepare.py
scripts/build.sh
```

首次需要下载 Android NDK、词库和语音模型，请预留至少 16 GB 空间。脚本使用系统已有的 HTTP 代理，校验下载文件，不修改全局 Java / SDK 设置。工具链放在 `.toolchain/`，模型放在 `.downloads/`，均不纳入 Git。

已有 Android Studio 或其他系统：使用 JDK 17、SDK 36、Build Tools 36.0.0、NDK 29.0.14206865、CMake 3.22.1，配置 `local.properties` 的 `sdk.dir`，再执行 `prepare.py` 和 `build.sh`。Gradle Wrapper 固定为 8.13。Android Studio 可以直接打开本目录，首次同步前先准备依赖和资源。

默认构建会执行核心单元测试、生成 Debug APK 并执行 Android Lint。输出：`app/build/outputs/apk/debug/app-debug.apk`。这是供个人测试的调试签名包；正式发布前要建立你自己保管的签名密钥。

```sh
# 构建 Android 原生集成测试 APK
scripts/build.sh :app:assembleDebugAndroidTest
# 审计最终安装包的权限、桌面入口、常见网络 API、ABI 与 16 KB 对齐
python3 scripts/check-apk.py app/build/outputs/apk/debug/app-debug.apk
```

安装后在系统设置中搜索“输入法”，进入屏幕键盘管理，启用「简心输入法」，再通过系统输入法选择器切换到简心。点系统输入法列表中的「简心输入法」设置入口，或键盘工具栏的「设置」，即可调整配置并试打。需要语音时允许麦克风。设置键盘高度和底部距离后，收起再打开键盘即可生效。

桌面和应用抽屉中没有简心启动图标；系统的应用管理、输入法列表仍会显示应用名称和身份。构建时在电脑上下载依赖与模型，不会给手机上的输入法增加联网能力。

## 依赖与许可

版本固定在 [third_party/versions.json](third_party/versions.json)，不会自动跟随 CyIME 更新。自身源码采用 GPL-3.0-or-later，第三方声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。分发时附上对应源码和依赖构建信息。

原始搜狗 APK 保持不变，只作使用体验参照。历史调研保留在 [设计核查](docs/custom-ime-design.md) 和 [CyIME 评估](docs/cyime-assessment.md)，其中早期的分支路线已被本独立工程取代。
