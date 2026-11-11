# RIME 编译补丁

基于锁定的 Xime librime 提交 1141816890b0d675dc1223f79cb0bd328d7b3fdd。

`0001-android-headers-outside-namespace.patch`：将 Android 的 `<chrono>` 与 `<android/log.h>` 引入移到 `namespace rime` 外，修复 NDK 29 的 `rime::std` 编译错误。同时让包含原始输入的诊断日志仅在显式定义 `JIANXIN_RIME_DIAGNOSTICS` 时启用（简心构建不定义），避免将日常输入写入 logcat。不改变候选算法。补丁由 `scripts/prepare.py` 检查后幂等应用，不重置源码目录。
