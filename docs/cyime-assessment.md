# CyIME 评估与定制路线更新

> 历史调研记录：下文“定制 CyIME 分支 / 尚未开发”的结论已由后续要求取代。当前简心采用独立多模块工程，只固定引用输入引擎、词库和语音依赖；实际入口与功能状态请看 [项目说明](../README.md) 和 [开发指南](development.md)。

2026-10-31。结论：**优先基于 CyIME 做定制分支，保留其现有 RIME 集成、九键增强和本地语音，围绕 Moto 适配与搜狗使用习惯做改进。** 前一版“语燕公开界面 + 新 RIME 桥接”保留为备选，不再作为首选实施路线。

本次固定读取 CyIME 的 `main` 提交 `5eba9938c8e3eb3a789685cda2d9df68c03ed1c7`。该提交的源码版本标记为 `1.15.14` / `20261366`；这不是对最新发布 APK 版本的判断。GitHub API 查询遇到限流，提交从仓库网页解析，随后使用固定提交地址下载部分源码。源码清单及内容哈希见 [核查基线](evidence/cyime-baseline.json)。未编译、安装或运行 CyIME，也未检查其发布 APK 内原生库的 ELF 对齐。

## 已有实现与仍需验证的内容

| 用户需求 | 本次确认的代码基础 | 定制工作 |
| --- | --- | --- |
| 保留 RIME，同时有成熟手机界面 | Kotlin / Compose 界面；RIME 子模块、JNI 和 CMake 构建路径 | 优先验证原工程能独立构建，保留已有输入算法，不重新写一套键盘 |
| 64 位 Android | ABI 配置包含 ARM64，源码指定 NDK 29；原生构建有 16 KB 相关设置 | 验证最终 ARM64 APK 的完整依赖，单独跑 4 KB / 16 KB 环境，不能只看构建参数 |
| 底部导航横条 | `ImeWindowInsets.kt` 读取系统 Insets，`ImeBottomSpace.kt` 转换底部安全区；系统区域与用户额外留白分别参与布局 | 在工作空间开启且横条可见时验证；只针对实际出现的覆盖、重复留白或背景问题修补 |
| 按钮位置可调整 | `ToolbarCustomizeView` 使用拖动排序；`QuickActionLayout` 管理工具栏、菜单和未使用项；配置保存有序动作 ID | 设置符合用户习惯的默认顺序，验证表情、剪贴板、语音的位置及升级后的保留 |
| 离线语音 | 已接入 sherpa-onnx，存在语音会话与收尾处理；内置模型脚本包含 Paraformer 与 SenseVoice | 在目标机比较识别速度、准确率和资源占用，决定是否开启二次校正；确认模型随交付可用 |
| 横竖屏稳定 | 服务配置变更会清除尺寸及光标区域缓存；固定/悬浮、横/竖屏高度有分别存储的实现 | 复现“横屏视频输入 → 切竖屏应用”，检查实际布局、组合输入和触摸区域；不能直接认定已经修好 Moto 问题 |

以上为源码事实，不是实机通过记录。证据：[构建配置](https://github.com/Cyletix/CyIME/blob/5eba9938c8e3eb3a789685cda2d9df68c03ed1c7/app/build.gradle.kts)、[RIME 构建](https://github.com/Cyletix/CyIME/blob/5eba9938c8e3eb3a789685cda2d9df68c03ed1c7/app/src/main/jni/cmake/Rime.cmake)、[工具栏编辑](https://github.com/Cyletix/CyIME/blob/5eba9938c8e3eb3a789685cda2d9df68c03ed1c7/app/src/main/java/com/kingzcheung/xime/ui/menubar/ToolbarCustomizeView.kt)、[系统安全区](https://github.com/Cyletix/CyIME/blob/5eba9938c8e3eb3a789685cda2d9df68c03ed1c7/app/src/main/java/com/kingzcheung/xime/service/ImeWindowInsets.kt)、[高度配置](https://github.com/Cyletix/CyIME/blob/5eba9938c8e3eb3a789685cda2d9df68c03ed1c7/app/src/main/java/com/kingzcheung/xime/settings/KeyboardHeightProfiles.kt)、[语音构建](https://github.com/Cyletix/CyIME/blob/5eba9938c8e3eb3a789685cda2d9df68c03ed1c7/app/build-logic/tasks-speech.gradle.kts)。

CyIME 派生自 Xime，应用采用 GPLv3。公开分发定制版时需要按许可证处理对应源码和声明，应用名称、图标及各模型另核各自规则。它已经将安装包名与源码 namespace 分开，适合创建自己的安装身份。当前代码最低 Android 9 / API 28、compileSdk 36、targetSdk 35。[项目说明](https://github.com/Cyletix/CyIME/blob/5eba9938c8e3eb3a789685cda2d9df68c03ed1c7/README.md)、[许可证](https://github.com/Cyletix/CyIME/blob/5eba9938c8e3eb3a789685cda2d9df68c03ed1c7/LICENSE)

RIME 核心有源码构建入口，但整个供应链仍包含预编译 ONNX Runtime、sherpa-onnx AAR 和模型文件。首轮构建应锁定子模块、补丁、依赖版本与模型哈希，核对 ARM64 闭包、下载来源及最终产物。本次没有将“提供构建脚本”写成“已成功构建”。

## 把“和搜狗一样好用”变成可验证目标

个人定制的目标是：在用户常用的输入方式和应用中，选词与改错负担接近现有搜狗，常用功能更顺手，并消除已列出的适配问题。当前没有两者同机对照数据，不能先宣称已达到这个水平。

| 体验层 | 需要做到的行为 | 检查方法 |
| --- | --- | --- |
| 按键手感 | 高度、间距、长按时机、振动、符号位置符合原习惯 | 在相同手机和应用中输入同一组内容，记录误触和多余步骤 |
| 中文选词 | 常用词、人名、地名、聊天句子尽量首选正确 | 比较首选/前五候选、额外选词次数、整句改字次数 |
| 九键或全拼 | 简拼、选音、错拼、混输及删除恢复符合预期 | 按用户实际按键过程回放，不仅向引擎一次性传完整拼音 |
| 个性化 | 用户选择后能学习，常用语和个人词条可导入、备份 | 分别测试新用户和相同学习记录下的结果 |
| 联想与纠错 | 有用且稳定；不在准备点击时把候选突然换位 | 对正常拼写和错拼分别评估，同时记录候选稳定性和响应延迟 |
| 语音 | 常用语句能正确识别，结束、取消和切输入框不串字 | 同一音频和实时说话两种测试；记录改字量、结束等待和资源占用 |
| 窗口稳定性 | 横条可见、旋转、分屏、切应用后仍正常输入 | 使用原设计中的 Moto 回归矩阵，记录实际设备结果 |

RIME 不是候选准确率的全部。词库、词频、语言模型、用户学习、九键解码、纠错、候选呈现和响应速度都会影响体感。CyIME 已有相关增强和算法回放工具，因此值得继承，但工具存在不等于质量已达标。其历史开发报告也不能当作当前版本或用户设备的性能成绩。[九键回归流程](https://github.com/Cyletix/CyIME/blob/5eba9938c8e3eb3a789685cda2d9df68c03ed1c7/docs/development/t9-algorithm-regression.md)

建议建立 100–200 条本地测试样例：用户主动提供并去除敏感内容的常用表达，搭配未参与调参的独立样例；包含短词、整句、简拼、人名地名、错拼及中英数字混输。同机比较现有搜狗离线版、固定版本 CyIME、定制版，统一输入模式、学习状态和网络状态。日常优化样例与独立验收样例分开，避免把测试答案加入词库后误判为普遍提升。

常用场景达到或超过个人原有体验是合理的工程目标；搜狗在所有领域的热词覆盖、云端语义预测、任意错拼和长句能力不是这次个人定制能够预先承诺的结果。优先优化用户实际用到的场景。

## 更新后的实施路径

1. **构建和体验基线。** 固定 CyIME 提交及其完整子模块、补丁、模型清单，先复现原有 ARM64 构建和基本输入，再建立独立包名与签名。保留现有 RIME 分支和九键插件；不要直接换成纯上游 librime 丢掉定制能力。原搜狗和原版 CyIME 可并存作对照。
2. **验证五个痛点。** 先用已有功能设置按钮顺序、底部留白和离线语音；在 Moto 工作空间与旋转场景中复现剩余问题。已经满足的部分直接继承，未满足的部分记录触发条件后修改。
3. **建立用户预设。** 定制键盘尺寸、配色、振动、长按、符号及工具顺序。默认以打字和语音为主；字幕、跨设备同步等扩展按需开启，先不做无关的大规模删改。
4. **输入质量迭代。** 先调整方案和词库，再根据具体失败样例修改解码或候选排序。每次兼顾质量、延迟与候选稳定性，保留独立回归样例和可回退基线。
5. **语音与发布验收。** 只交付选定且验证过的模型组合，检查权限、断网、首次启动、切换输入框、连续使用内存，以及 APK 升级后设置和个人词库的保留。

语音方案先沿用项目实际接入路径，不重新接一套识别框架。固定提交的 `prepare-bundled-models.py` 包含 Paraformer + SenseVoice，而较早的适配规划提到 Zipformer；实施应以选定提交的代码和模型清单为准，避免把不同日期的说明混用。[模型打包脚本](https://github.com/Cyletix/CyIME/blob/5eba9938c8e3eb3a789685cda2d9df68c03ed1c7/scripts/prepare-bundled-models.py)

CyIME 的“本地离线识别”不等于整个应用禁止联网。当前 Manifest 包含联网、麦克风及其他扩展功能权限；如用户要求严格离线，应另做不含联网权限的构建，并处理模型交付及依赖联网的功能入口，不能只关闭一个开关就宣称完全离线。[Manifest](https://github.com/Cyletix/CyIME/blob/5eba9938c8e3eb3a789685cda2d9df68c03ed1c7/app/src/main/AndroidManifest.xml)

## 具体改动入口

| 工作 | 优先检查的位置 |
| --- | --- |
| 导航区与额外留白 | `service/ImeWindowInsets.kt`、`service/ImeBottomSpace.kt`、服务中的 `contentBottomPaddingDp` / `activeBottomDp` |
| 旋转后状态、尺寸和触摸区域 | `XimeInputMethodService.onConfigurationChanged()`、`onStartInputView()`、`onComputeInsets()`、`settings/KeyboardHeightProfiles.kt` |
| 工具栏排序与保存 | `ui/menubar/ToolbarCustomizeView.kt`、`keyboard/QuickActionLayout.kt`、`keyboard/ToolbarButton.kt`、`SettingsPreferences` |
| 语音模型与会话 | `service/VoiceRecognitionHandler.kt`、`speech/AsrSupport.kt`、`model/BundledModelInstaller.kt`、语音构建与模型脚本 |
| 选词质量与算法回归 | `jni/cmake/Rime.cmake`、RIME 补丁、九键插件、`tools/ime_lab/t9_replay.py` |
| 独立构建 | `app/build.gradle.kts`、`.gitmodules`、`jni/CMakeLists.txt`、构建任务及对应模型清单 |

无需先把 CyIME 全部重写，也不把语燕的旋转修复假设直接套在 CyIME 上。当前适合推进的首个交付是“可自行构建、设置符合用户习惯、能在 Moto 上验证五个痛点的定制测试版”。机型、系统版本、主用输入方案和语音偏好仍待用户确认；这些决定测试配置与模型选择。
