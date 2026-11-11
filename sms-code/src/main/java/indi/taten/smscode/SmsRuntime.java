package indi.taten.smscode;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import android.os.SystemClock;

/** 验证码只在内存短暂显示；SharedPreferences、文件和日志均不保存正文或验证码。 */
public final class SmsRuntime {
    public static final String CHANGED = "indi.taten.smscode.STATUS_CHANGED";
    public static volatile boolean connected;
    public static volatile String status = "等待新短信或手动文本";
    private static volatile String code = "";
    private static volatile long copiedAt;
    public static CopyRequest pendingCopy;
    private static PendingSms pendingSms;
    public static final class PendingSms {
        public final String sender, body;
        public final long receivedAt=SystemClock.elapsedRealtime();
        public PendingSms(String sender,String body) {this.sender=sender;this.body=body;}
    }
    public static PendingSms pendingSms() {
        if(pendingSms!=null && SystemClock.elapsedRealtime()-pendingSms.receivedAt>600000)pendingSms=null;
        return pendingSms;
    }
    public static void receivedSms(String sender,String body) {
        PendingSms current=new PendingSms(sender,body);pendingSms=current;
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{if(pendingSms==current)pendingSms=null;},600000);
    }
    public static void forgetPendingSms() {pendingSms=null;}
    public static void consumeSms(String body) {if(pendingSms!=null && pendingSms.body.equals(body))pendingSms=null;}
    public static final class CopyRequest {
        public final String code;
        public final long expires = SystemClock.elapsedRealtime()+5000;
        public final java.util.function.Consumer<Boolean> completion;
        public CopyRequest(String code, java.util.function.Consumer<Boolean> completion) {this.code=code;this.completion=completion;}
    }
    public static String recentCode() {
        if(SystemClock.elapsedRealtime()-copiedAt>=120000) code="";
        return code;
    }
    public static boolean copyVisibleCode(Context context, String value) {
        // 删除或滚动后露出的旧消息，不能覆盖刚接收或明确复制的验证码。
        // 新短信接收及用户主动复制仍走 copy，可立即更新剪贴板。
        String recent=recentCode();
        if(!recent.isEmpty() && !recent.equals(value))return false;
        return copy(context,value,"短信页面");
    }
    public static boolean copy(Context context, String value, String source) {
        try {
            ClipData clip = ClipData.newPlainText("验证码", value);
            PersistableBundle extras = new PersistableBundle(); extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
            clip.getDescription().setExtras(extras);
            ClipboardManager clipboard = context.getSystemService(ClipboardManager.class);
            clipboard.setPrimaryClip(clip);
            code = value; copiedAt = SystemClock.elapsedRealtime();
            long version=copiedAt;
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{if(copiedAt==version)code="";},120000);
            // 有些系统在后台静默拒绝写入；未确认写入时不能继续删除。
            ClipData actual = clipboard.getPrimaryClip();
            if (actual == null || actual.getItemCount() != 1 || actual.getItemAt(0).getText() == null
                    || !value.contentEquals(actual.getItemAt(0).getText())) {
                setStatus(context, "已识别验证码并请求写入剪贴板，可在输入框粘贴"); return false;
            }
            setStatus(context, "已从" + source + "复制验证码");
            return true;
        } catch (RuntimeException failure) {
            setStatus(context, "系统未允许复制，本次不会删除短信"); return false;
        }
    }
    public static void setStatus(Context context, String value) {
        status = value;
        context.sendBroadcast(new Intent(CHANGED).setPackage(context.getPackageName()));
    }
}
