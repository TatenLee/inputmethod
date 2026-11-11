# AndroidX Test 引用的编译期静态检查注解，不参与运行；仅测试 APK 忽略这两类。
# 与 R8 输出的 debugAndroidTest/missing_rules.txt 一致，不放宽应用包检查。
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.MustBeClosed
