# 简心独立工具测试版（工作空间 0.2.0 / 验证码 0.1.0）

这是两个独立 APK，不依赖简心输入法，也不把短信能力放进输入法。两者有桌面设置入口，输入法原有的无桌面图标设计不变。

| 工具 | 包名 | 系统要求 |
| --- | --- | --- |
| 简心·工作空间 | `indi.taten.workprofile` | Android 12+，系统提供受支持的隐藏横条配置 |
| 简心验证码 | `indi.taten.smscode` | Android 11+，接收短信用于后台复制；页面识别/删除另需无障碍 |

两个 APK 都没有联网权限，不使用共享 UID、联网 SDK、在线规则或在线升级；无原生库，可在 32 位和 64 位 Android 上安装。工作空间 APK 不声明任何 `uses-permission`；验证码 APK 仅声明用户已同意的 `RECEIVE_SMS`（接收新短信），不申请读取历史短信或发送短信权限，不要求 Root、ADB、通知监听或成为默认短信 App。工作空间工具需要下述 ADB 激活。

## 工作空间横条

1. 把 `profile-control-0.2.0-offline-debug.apk` 和 `activate-profile-control.py` 放在同一文件夹。工具包装在主空间即可，不需要克隆到 Shelter。
2. 电脑安装 Python 3.9+ 和 Android Platform Tools。手机打开 USB 调试，通过 USB 连接并允许这台电脑调试。
3. 终端切换到工具包文件夹，运行：

   ```sh
   python3 activate-profile-control.py
   ```

   在本项目目录可直接运行：

   ```sh
   cd /Users/shawn/Code/inputmethod
   python3 scripts/activate-profile-control.py
   ```

   脚本会安装或更新 APK 并打开应用。找不到 ADB 时用 `--adb /实际路径/adb`；多个设备时用 `--serial 设备编号`。本 Mac 的 ADB 在 `/Users/shawn/Code/inputmethod/.toolchain/android-sdk/platform-tools/adb`。

4. 在 APK 中选择关联工作空间的“隐藏横条”或“显示横条”。首次修改前会保存该空间原状态，可点“恢复首次修改前的状态”。
5. 在带公文包标记的应用中收起再打开键盘查看。激活脚本本身不改导航设置。

激活后可断开电脑；结束控制连接、强行停止、进程被系统回收或手机重启后，需要重新运行激活脚本。控制连接失效与已写入的系统配置是两件事：配置通常由系统持久保存；本版没有逐机型验证重启后状态。点“结束本次控制连接”会撤销本次借用的调试权限，不自动恢复横条配置。

### 支持范围与原理

本版只操作系统已有的 `com.android.internal.systemui.navbar.hidegestural` overlay。还会检查它的目标是 `android`、类别是 `com.android.internal.navigation_bar_mode_hide`、允许修改且状态正常。没有这个配置时显示不支持，不尝试猜测厂商键值，也不切换三键/手势导航方式。

脚本通过 ADB 启动 APK 内的 instrumentation，借用跨用户、修改 overlay、查询应用及打开受保护权限页面所需的五个 shell 权限；shell 进程只把系统 overlay Binder 引用交给应用。连接入口要求系统 `DUMP` 权限且调用 UID 必须为 ADB shell。手机端的 `am instrument -w` 通过 `nohup` 保持会话，因此电脑端脚本可以退出。

操作前重新核对工作空间与当前用户的关联关系、空间类型和序列号；备份还绑定系统构建指纹，防止把旧空间的记录套用到重新创建的空间。每次写入后读回目标状态并核对主空间没有变化。不修改 Shelter 的管理员限制，不处理私人空间，不提供包卸载功能。

Moto X40 / Android 16 上此前已用同一系统配置成功隐藏工作空间横条，用户确认消失。**新 APK 的激活与空间发现已经过模拟器检查；新 APK 在 Moto 上的显示/隐藏/恢复按钮仍需实机验收。** 旧备份包及旧维护命令继续保留，见 [原有 Moto 维护说明](moto-work-profile.md)。

## 工作应用权限（0.2.0 新增）

使用与横条相同的 ADB 激活方式。在主空间打开“简心·工作空间”，点击对应空间的“工作应用权限”，搜索并选择应用。列表来自系统的工作应用启动入口；没有桌面入口的应用可尝试“按包名打开”。选择允许/拒绝等操作均在系统页面完成，工具自身不调用权限授予或撤销 API。

Moto X40 / Android 16 的常规入口存在工作空间兼容问题：系统权限控制器把工作应用页面转交给主空间的 Moto DeviceGuard，后者未正确加载工作应用的数据，导致只有标题。权限记录仍存在。此版本提供替代入口，携带应用包名和工作空间的 UserHandle，通过系统权限控制器自带导航图打开原生应用权限页面。

**这不会修复“桌面长按 → 应用信息 → 权限”的原始入口。** 原始入口仍进入 Moto 页面，可能继续空白。本工具不修改系统 APK，不更换全局默认权限管理器，不修改地区属性，也不解除 Shelter 的设备管理限制。

原生页面的启动权限通过 ADB 调试会话临时借用。结束会话或重启后，重新进入本工具的权限页面需要电脑再次激活；用户已经选择的权限状态由 Android 保存。已在这台 Moto 上打开带公文包标记的懂车帝权限列表和麦克风的三个选项；验证过程没有改变懂车帝的 16 项运行时权限记录。其他 ROM 和后续系统更新可能需要适配。

## 验证码工具

1. 安装 `sms-code-0.1.0-offline-debug.apk`，打开“简心验证码”。
2. 选择目前使用的短信 App；默认短信 App 不会被替换。
3. 点击“允许接收新短信（后台复制）”，允许系统短信权限。**只需要后台复制时，无须开启无障碍。** 系统权限组可能统称“短信”，APK 实际只申请接收，不申请发送或读取历史。
4. 如需页面识别或辅助删除，再点击“打开无障碍设置”，在系统设置里启用“简心验证码”。部分系统对侧载 APK 要求先在应用信息页允许受限制的设置；仅按手机实际提示操作。
5. 保持“自动识别并复制”开启。默认保留短信。可先在“本地试一下”粘贴模拟文本验证识别，也可从短信 App 分享单条文本到本工具。

接收权限获准后，工具通过系统 `SMS_RECEIVED` 广播直接处理新短信，合并同一条短信的分段，完全在本机提取并复制，不依赖通知是否隐藏。接收器由系统 `BROADCAST_SMS` 权限保护，不接受普通 App 伪造短信广播，也不中断默认短信 App 的接收。用户拒绝接收权限时仍可使用无障碍通知/页面识别。无障碍只处理选定 App 的文本；其他应用的窗口事件仅用于确认是否已离开短信页面。锁屏时不处理。

识别规则要求明确的验证码语境，支持中文/英文、全角数字及常见数字字母组合；遇到多个不同候选不猜测。规则保守，可能漏掉特殊格式，后续通过更新 APK 改进。没有在线规则服务。

### 删除模式

- **保留短信**：默认值，只复制。
- **显示删除按钮**：在已适配的短信详情页面显示“复制并删除此条验证码”。点击后先打开短暂的复制确认窗，取得正常前台焦点，写入并读回验证码；确认成功后返回短信页面，只操作已定位的那条消息。
- **自动删除新验证码**：需要另行开启。必须有十分钟内接收到的新短信（或可读通知）的正文及发送方、当前默认短信 App 和会话标题匹配、短信正文完全匹配、当前位于会话底部且可见匹配只有一条。仍需打开短信页面，仍先执行复制确认。未收到正文、无法匹配或系统拒绝操作时保留短信，退回手动按钮或停止。

本版删除适配器仅面向 `com.android.messaging` 的 AOSP Messaging 单条消息界面，支持明确的中英文单条删除确认文字。其他短信 App 只支持复制，删除选项不可用。厂商使用相同包名但改动界面的版本，也可能被保护规则拒绝。

工具不会把移除通知当成删除短信，不批量清空会话，不直接写短信数据库；不点击未知删除确认框。删除后本工具不能恢复原短信。执行清理时请等待界面操作结束。

**已在 Android 15 模拟器验证单条短信实际删除。** 手动按钮、打开会话后自动删除两种模式均只移除了指定的合成验证码短信；通过测试端读取短信数据库，确认普通短信及其他消息保持原样，并在删除后验证剪贴板仍是刚复制的新验证码。

测试环境自带 AOSP Messaging 的清单缺少两项旧权限，导致其原生删除功能不可用。为完成上述验证，仅在模拟器中给原短信 App 补充 `RECEIVE_WAP_PUSH` 与 `READ_CELL_BROADCASTS` 声明，保留其代码和资源，使用匹配的公开 AOSP 开发签名。测试后已卸载该测试更新、恢复原短信 App；补丁不包含在交付包中，验证码工具也没有增加这些权限。这些结果不能代替 Moto 厂商短信界面的实机验收。

Android 10+ 限制后台读剪贴板。因此后台写入与“已读回确认”在实现中分开，删除必须等前台复制确认成功。Android 15+ 也可能限制验证码通知和敏感无障碍内容；本工具没有冒充面向残障人士的无障碍工具，也不关闭系统保护。已在 Android 15 模拟器关闭无障碍、应用退到后台的条件下验证：直接接收短信并复制成功，随后到达的普通短信不会覆盖验证码。无法保证所有厂商的后台限制完全相同；本版删除始终需要打开短信页面。

刚复制后的两分钟内，页面中露出的其他验证码不会自动覆盖当前结果，避免删除或滚动后旧验证码替换新验证码。新收到的短信以及用户主动复制仍可立即更新剪贴板。

不保存短信或验证码历史到文件或数据库。识别结果仅在内存短暂显示；去重与待匹配正文仅作有时限的内存缓存。设置只保存选定包名和开关。复制内容标记为敏感，系统及原短信 App 自身的数据保留策略仍由它们管理。

## 代码从哪里看

```text
profile-control/
  MainActivity.java                 工作空间设置页面
  PermissionsActivity.java          工作应用搜索与系统权限页入口
  control/PermissionPages           校验目标应用并构造原生权限页面导航
  control/ControlInstrumentation    ADB 激活与权限生命周期
  control/OverlayShellBridge        shell 侧系统服务引用交接
  control/OverlayProvider           仅 shell 可访问的连接入口
  control/ControlSession            工作空间发现、状态读取、备份与写入
  control/ProfileRules              可独立测试的范围检查
sms-code/
  MainActivity.java                 短信 App 选择、开关、本地文本试用
  CopyActivity.java                 删除前的前台复制确认
  SmsReceiver.java                  系统新短信广播入口，无历史数据库访问
  SmsPreferences / SmsRuntime       设置与短暂内存状态
  core/OtpExtractor                 自有离线提取规则
  core/DeletePolicy                 自动删除前提与单条确认文字
  service/CodeAccessibilityService  通知、当前页面、单条删除状态机
tool-tests/                         两个 APK 共用的离线系统测试
scripts/activate-profile-control.py 电脑激活入口
```

Java 文件的完整路径位于各模块的 `src/main/java/indi/taten/...`。这是独立重写，没有把 Xposed Hook 改名后包装成普通应用，也没有复制其联网更新和在线规则模块。功能参考用户提供的 [Xposed 模块发布仓库](https://github.com/Xposed-Modules-Repo/com.github.tianma8023.xposed.smscode)；调研过其后续 [XposedSmsCode 源码](https://github.com/magisk317/XposedSmsCode) 的 `656816204fd0e3fad071be87210d45888e23d47c` 版本。自身源码沿用本工程 GPL-3.0-or-later。

构建只需 JDK 17、SDK 36、Build Tools 36.0.0、Gradle 8.13，不需要输入法的 NDK 或语音模型：

```sh
python3 scripts/run-gradle.py :profile-control:assembleDebug :sms-code:assembleDebug
python3 scripts/run-gradle.py :profile-control:testDebugUnitTest :sms-code:testDebugUnitTest :profile-control:lintDebug :sms-code:lintDebug
# 只对模拟器运行：安装测试包，验证网络隔离、前台复制和关闭无障碍后的新短信后台复制
python3 scripts/run-gradle.py :profile-control:assembleDebugAndroidTest :sms-code:assembleDebugAndroidTest
python3 scripts/test-offline-tools.py --serial emulator-5554
```

每个模块构建时检查合并后的 manifest：工作空间工具允许的应用权限为空，验证码工具只允许 `RECEIVE_SMS`；拒绝额外权限、共享 UID 和明文网络配置。源码包内另附仅含这两个工具的 Gradle 工程，可单独打开、构建和维护。这是调试签名测试版；正式对外发布前需要由你保管正式签名密钥。

依据：[Android 默认短信 App 的数据库写权限](https://developer.android.com/reference/android/provider/Telephony)、[后台剪贴板限制](https://developer.android.com/about/versions/10/privacy/changes#clipboard-data)、[Android 15 验证码通知保护](https://developer.android.com/about/versions/15/behavior-changes-all#otp-redaction)、[UiAutomation 调试身份](https://developer.android.com/reference/android/app/UiAutomation#adoptShellPermissionIdentity(java.lang.String...))、[Android 运行时资源覆盖](https://source.android.com/docs/core/runtime/rros)。
