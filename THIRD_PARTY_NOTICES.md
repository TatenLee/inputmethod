# 简心输入法 · 第三方组件

简心的 Android 应用、键盘、设置、语音管理和 JNI 桥接代码独立维护，采用 GPL-3.0-or-later。未复制或修改原搜狗 APK。发布时应同时提供本工程对应源码、依赖锁定信息和构建脚本。

| 组件 | 来源 | 许可证 |
| --- | --- | --- |
| librime（与核查的 CyIME 使用相同的 Xime 分支提交） | https://github.com/ximeiorg/librime/tree/1141816890b0d675dc1223f79cb0bd328d7b3fdd | BSD-3-Clause，见组件 LICENSE |
| 雾凇拼音词库 8105 / base / ext | https://github.com/iDvel/rime-ice/tree/9e66b0729083b37d217312294f6d516c8d7234be | GPL-3.0；各字典头部保留来源与署名 |
| Boost 1.89.0 | https://www.boost.org/ | BSL-1.0 |
| UTF8-CPP（随 librime） | https://github.com/nemtrif/utfcpp | 随附许可声明 |
| LevelDB | https://github.com/google/leveldb | BSD-3-Clause |
| marisa-trie | https://github.com/s-yata/marisa-trie | BSD-2-Clause / LGPL，见 COPYING.md |
| OpenCC | https://github.com/BYVoid/OpenCC | Apache-2.0；包含其第三方头文件声明 |
| yaml-cpp | https://github.com/jbeder/yaml-cpp | MIT |
| sherpa-onnx 1.13.8 | https://github.com/k2-fsa/sherpa-onnx/tree/v1.13.8 | Apache-2.0；官方 AAR 内含 ONNX Runtime 及其他依赖 |
| SenseVoice 量化模型 | https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/tree/2365baeacb507f821a0c8120fcee3d484dba7a07 | Apache-2.0，以附带模型卡为准 |
| ONNX Runtime | https://github.com/microsoft/onnxruntime | MIT |
| Kotlin 标准库 | https://github.com/JetBrains/kotlin/tree/v2.1.20 | Apache-2.0 |
| Android NDK libc++ | https://android.googlesource.com/platform/ndk/ | LLVM / 第三方许可，见附带通知 |

完整版本、提交、补丁和下载校验值在 `third_party/versions.json`。本地 RIME 补丁仅修复 Android 头文件位置，并默认关闭包含原始输入的诊断日志，详见 `third_party/patches/README.md`。语音模型的识别能力来自 SenseVoice / sherpa-onnx；本项目不宣称训练了这些模型。

CyIME / Xime 为架构调研来源。0.1.0 未引入 CyIME 的完整应用界面、增强九键插件、纠错补丁或个人语料调权。后续可以逐项评估和移植，届时记录对应提交、变更与许可。当前九键由 RIME 拼写运算实现，不代表已达到 CyIME 或搜狗的输入质量。
