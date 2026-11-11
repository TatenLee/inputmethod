# 只剔除未使用的依赖代码，保留应用源码对应的类名与方法名，方便阅读和排错。
-dontobfuscate
-keep class indi.tatenlee.inputmethod.** { *; }

# JNI 通过原始名称访问 sherpa 配置对象、字段与方法，必须完整保留。
-keep class com.k2fsa.sherpa.onnx.** { *; }

# AndroidX Test 与应用共用标准库；应用裁剪不能删除仅由测试使用的运行时入口。
# URL 读取所在的 TextStreamsKt 仍按实际引用裁剪，未使用的网络辅助方法不入包。
-keep class !kotlin.io.TextStreamsKt,kotlin.** { *; }
