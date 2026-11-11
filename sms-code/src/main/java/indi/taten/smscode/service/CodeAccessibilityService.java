package indi.taten.smscode.service;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import indi.taten.smscode.SmsPreferences;
import indi.taten.smscode.SmsRuntime;
import indi.taten.smscode.CopyActivity;
import indi.taten.smscode.core.DeletePolicy;
import indi.taten.smscode.core.OtpExtractor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** 无 Root / 无短信数据库权限；仅处理用户选定的短信 App。 */
public final class CodeAccessibilityService extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SmsPreferences preferences;
    private final LinkedHashMap<String,Long> seenNotifications = new LinkedHashMap<>();
    private String copiedBody = "";
    private long copiedAt;
    private String pendingBody;
    private String pendingSender;
    private long pendingAt;
    private Button chip;
    private String chipBody;
    private Flow flow;
    private boolean copyingForDeletion;
    private String attemptedBody="";
    private long attemptedAt;
    private int generation;
    private final Runnable scan = this::scanVisible;
    private final Runnable expire = new Runnable() {
        @Override public void run() {
            long now=SystemClock.elapsedRealtime();
            seenNotifications.entrySet().removeIf(entry->now-entry.getValue()>DeletePolicy.MAX_AGE_MS);
            if(now-pendingAt>DeletePolicy.MAX_AGE_MS)pendingBody=null;
            if(now-copiedAt>120000)copiedBody="";
            if(now-attemptedAt>120000)attemptedBody="";
            SmsRuntime.recentCode();handler.postDelayed(this,60000);
            SmsRuntime.pendingSms();
        }
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceChanged = (p,k) -> {
        generation++;cancelFlow(); removeChip(); pendingBody = null; SmsRuntime.pendingCopy=null;copyingForDeletion=false;
    };
    private static final class Flow {
        final String body; final long deadline;
        int stage; // 0: waiting selection menu; 1: waiting single-message confirmation; 2: verifying removal
        Flow(String body) { this.body = body; deadline = SystemClock.elapsedRealtime()+12000; }
    }
    @Override protected void onServiceConnected() {
        preferences = new SmsPreferences(this);
        preferences.store().registerOnSharedPreferenceChangeListener(preferenceChanged);
        SmsRuntime.connected = true; SmsRuntime.setStatus(this, "已连接，仅处理选定短信 App");
        handler.postDelayed(expire,60000);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (preferences == null || event == null) return;
        String pkg = event.getPackageName() == null ? "" : event.getPackageName().toString();
        if (!pkg.equals(preferences.smsPackage())) {
            // 不读取其他应用文本。稍后检查活动根窗口的包名，避免把系统剪贴板浮层当成离开短信。
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    && (!pkg.equals(getPackageName()) || "indi.taten.smscode.MainActivity".contentEquals(event.getClassName()==null?"":event.getClassName()))) {
                handler.removeCallbacks(scan); handler.postDelayed(scan,220);
            }
            return;
        }
        if (locked()) { cancelFlow(); removeChip(); return; }
        if (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            incoming(event); return;
        }
        handler.removeCallbacks(scan); handler.postDelayed(scan, 220);
    }
    private boolean locked() { return getSystemService(KeyguardManager.class).isKeyguardLocked(); }
    private void incoming(AccessibilityEvent event) {
        if (!preferences.autoCopy()) return;
        String body = null, sender = null;
        if (event.getParcelableData() instanceof Notification) {
            Notification n = (Notification)event.getParcelableData();
            CharSequence text = n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
            if (text == null) text = n.extras.getCharSequence(Notification.EXTRA_TEXT);
            if (text != null) body = text.toString();
            CharSequence title=n.extras.getCharSequence(Notification.EXTRA_TITLE);
            if(title!=null)sender=title.toString();
        }
        boolean completeBody = body != null;
        if (body == null) body = android.text.TextUtils.join(" ",event.getText());
        String code = OtpExtractor.extract(body);
        if (code == null) return;
        body = OtpExtractor.normalize(body);
        long now = SystemClock.elapsedRealtime();
        Long previous = seenNotifications.get(body);
        if (previous != null && now - previous < DeletePolicy.MAX_AGE_MS) return;
        seenNotifications.put(body,now);
        while (seenNotifications.size()>32) seenNotifications.remove(seenNotifications.keySet().iterator().next());
        if (completeBody && sender!=null) { pendingBody = body; pendingSender=sender; pendingAt = now; }
        copy(body,"短信通知",false);
    }
    private boolean copy(String body, String source, boolean fromPage) {
        String code = OtpExtractor.extract(body);
        if (code == null || locked()) return false;
        body = OtpExtractor.normalize(body);
        if (body.equals(copiedBody) && SystemClock.elapsedRealtime()-copiedAt<120000) return true;
        if (body.equals(attemptedBody) && SystemClock.elapsedRealtime()-attemptedAt<120000) return false;
        attemptedBody=body;attemptedAt=SystemClock.elapsedRealtime();
        if (!(fromPage ? SmsRuntime.copyVisibleCode(this,code) : SmsRuntime.copy(this,code,source))) return false;
        copiedBody=body; copiedAt=SystemClock.elapsedRealtime();
        android.widget.Toast.makeText(this,"验证码已复制",android.widget.Toast.LENGTH_SHORT).show();
        return true;
    }
    private AccessibilityNodeInfo smsRoot() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null || !root.getPackageName().toString().equals(preferences.smsPackage())) return null;
        return root;
    }
    private List<AccessibilityNodeInfo> bodies(AccessibilityNodeInfo root) {
        if (DeletePolicy.AOSP.equals(preferences.smsPackage()))
            return root.findAccessibilityNodeInfosByViewId(DeletePolicy.AOSP+":id/message_text");
        ArrayList<AccessibilityNodeInfo> result = new ArrayList<>();
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>(); queue.add(root);
        for (int count=0; !queue.isEmpty() && count<350; count++) {
            AccessibilityNodeInfo node = queue.removeFirst();
            if (node.isVisibleToUser() && !node.isEditable() && !node.isPassword() && node.getText()!=null
                    && OtpExtractor.extract(node.getText().toString())!=null) result.add(node);
            for(int i=0;i<node.getChildCount();i++) { AccessibilityNodeInfo child=node.getChild(i); if(child!=null)queue.add(child); }
        }
        return result;
    }
    private List<AccessibilityNodeInfo> exact(AccessibilityNodeInfo root,String body) {
        ArrayList<AccessibilityNodeInfo> matches=new ArrayList<>();
        for(AccessibilityNodeInfo node:bodies(root)) if(node.isVisibleToUser() && node.getText()!=null
                && OtpExtractor.normalize(node.getText().toString()).equals(body))matches.add(node);
        return matches;
    }
    private void scanVisible() {
        if(copyingForDeletion)return;
        if (preferences==null || locked()) { cancelFlow();removeChip();return; }
        AccessibilityNodeInfo root=smsRoot();
        if(root==null) { cancelFlow();removeChip();return; }
        if(flow!=null) { advanceFlow(root);return; }
        if(!preferences.autoCopy()) { removeChip();return; }
        AccessibilityNodeInfo chosen=null; String code=null; int bottom=-1;
        for(AccessibilityNodeInfo node:bodies(root)) {
            if(!node.isVisibleToUser() || node.getText()==null)continue;
            String candidate=OtpExtractor.extract(node.getText().toString()); if(candidate==null)continue;
            if(!DeletePolicy.AOSP.equals(preferences.smsPackage()) && code!=null && !code.equals(candidate)) { removeChip();return; }
            Rect bounds=new Rect();node.getBoundsInScreen(bounds);
            if(chosen==null || bounds.bottom>bottom) { chosen=node;code=candidate;bottom=bounds.bottom; }
        }
        if(chosen==null) { removeChip();return; }
        String body=OtpExtractor.normalize(chosen.getText().toString());
        copy(body,"短信页面",true);
        if(preferences.deletionMode()==SmsPreferences.KEEP || !DeletePolicy.AOSP.equals(preferences.smsPackage())) { removeChip();return; }
        int matches=exact(root,body).size();
        if(matches!=1) { removeChip();SmsRuntime.setStatus(this,"存在相同内容的多条短信，未执行删除");return; }
        SmsRuntime.PendingSms direct=SmsRuntime.pendingSms();
        if(direct!=null && direct.receivedAt>=pendingAt) {pendingBody=direct.body;pendingSender=direct.sender;pendingAt=direct.receivedAt;}
        if(preferences.deletionMode()==SmsPreferences.AUTO_DELETE && currentConversationMatches(root) && DeletePolicy.canPrepareAutoDelete(preferences.smsPackage(),body,pendingBody,
                SystemClock.elapsedRealtime(),pendingAt,matches,true)) {
            pendingBody=null;requestDeletion(body);return;
        }
        showChip(body);
    }
    private void showChip(String body) {
        if(chip!=null && body.equals(chipBody))return;
        removeChip();chipBody=body;
        chip=new Button(this);chip.setText("复制并删除此条验证码");chip.setAllCaps(false);
        chip.setOnClickListener(v->requestDeletion(body));
        WindowManager.LayoutParams layout=new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        layout.gravity=Gravity.BOTTOM|Gravity.END;layout.y=Math.round(92*getResources().getDisplayMetrics().density);layout.x=16;
        try { getSystemService(WindowManager.class).addView(chip,layout); }
        catch(RuntimeException failure) { chip=null;SmsRuntime.setStatus(this,"无法显示删除按钮，短信保留"); }
    }
    private boolean currentConversationMatches(AccessibilityNodeInfo root) {
        if(!preferences.smsPackage().equals(android.provider.Telephony.Sms.getDefaultSmsPackage(this)))return false;
        AccessibilityNodeInfo title=unique(root,DeletePolicy.AOSP+":id/conversation_title");
        AccessibilityNodeInfo list=unique(root,"android:id/list");
        if(title==null || title.getText()==null || pendingSender==null || list==null
                || !OtpExtractor.normalize(pendingSender).equals(OtpExtractor.normalize(title.getText().toString())))return false;
        // 只在会话底部处理新通知，滚动浏览旧消息时不自动删除。
        return !list.getActionList().contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
    }
    private void removeChip() {
        if(chip!=null) { try { getSystemService(WindowManager.class).removeView(chip); } catch(IllegalArgumentException ignored) {} }
        chip=null;chipBody=null;
    }
    private void requestDeletion(String body) {
        removeChip();
        if(copyingForDeletion || locked() || preferences.deletionMode()==SmsPreferences.KEEP)return;
        AccessibilityNodeInfo root=smsRoot();
        String code=OtpExtractor.extract(body);
        if(root==null || code==null || exact(root,body).size()!=1)return;
        copyingForDeletion=true;
        SmsRuntime.consumeSms(body);
        final int version=generation;
        SmsRuntime.CopyRequest request=new SmsRuntime.CopyRequest(code,confirmed->handler.postDelayed(()-> {
            copyingForDeletion=false;
            if(confirmed && version==generation && preferences!=null && SmsRuntime.connected)startDeletion(body,true);
            else SmsRuntime.setStatus(this,"未确认复制成功，短信保留");
        },650));
        SmsRuntime.pendingCopy=request;
        try {startActivity(new android.content.Intent(this,CopyActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));}
        catch(RuntimeException denied) {SmsRuntime.pendingCopy=null;copyingForDeletion=false;SmsRuntime.setStatus(this,"系统未允许复制确认窗，短信保留");}
        handler.postDelayed(()->{if(SmsRuntime.pendingCopy==request){SmsRuntime.pendingCopy=null;copyingForDeletion=false;SmsRuntime.setStatus(this,"复制确认超时，短信保留");}},5100);
    }
    private void startDeletion(String body,boolean confirmed) {
        removeChip();
        if(locked() || !DeletePolicy.AOSP.equals(preferences.smsPackage()) || preferences.deletionMode()==SmsPreferences.KEEP)return;
        AccessibilityNodeInfo root=smsRoot();if(root==null)return;
        List<AccessibilityNodeInfo> matches=exact(root,body);
        if(matches.size()!=1 || !confirmed)return;
        // AOSP 正文 TextView 报告 longClickable，但实际长按监听在消息行上。
        // 只接受消息列表的直接子项，不能一路向上选中会话或整个列表。
        AccessibilityNodeInfo target=matches.get(0).getParent();
        for(int level=0;level<6 && target!=null;level++) {
            AccessibilityNodeInfo parent=target.getParent();
            if(parent!=null && "android:id/list".equals(parent.getViewIdResourceName()) && target.isLongClickable())break;
            target=parent;
            if(level==5)target=null;
        }
        if(target==null || !target.isLongClickable()) { SmsRuntime.setStatus(this,"当前短信界面无法安全选中单条消息，短信保留");return; }
        flow=new Flow(body);pendingBody=null;
        if(!target.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) { cancelFlow();SmsRuntime.setStatus(this,"无法选中消息，短信保留");return; }
        handler.postDelayed(scan,350);
    }
    private AccessibilityNodeInfo unique(AccessibilityNodeInfo root,String id) {
        List<AccessibilityNodeInfo> found=root.findAccessibilityNodeInfosByViewId(id);
        AccessibilityNodeInfo selected=null;
        for(AccessibilityNodeInfo node:found) if(node.isVisibleToUser() && node.isEnabled()) { if(selected!=null)return null;selected=node; }
        return selected;
    }
    private void advanceFlow(AccessibilityNodeInfo root) {
        if(flow==null)return;
        if(SystemClock.elapsedRealtime()>flow.deadline) { cancelFlow();SmsRuntime.setStatus(this,"删除流程未完成，已停止操作");return; }
        if(flow.stage==0) {
            if(exact(root,flow.body).size()!=1) { cancelFlow();return; }
            AccessibilityNodeInfo delete=unique(root,DeletePolicy.AOSP+":id/action_delete_message");
            if(delete!=null) {
                flow.stage=1;
                if(!delete.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { cancelFlow();return; }
            }
        } else if(flow.stage==1) {
            AccessibilityNodeInfo message=unique(root,"android:id/message"), title=unique(root,"android:id/alertTitle"), confirm=unique(root,"android:id/button1");
            boolean single=DeletePolicy.isSingleMessageConfirmation(message==null || message.getText()==null?null:message.getText().toString())
                    || DeletePolicy.isSingleMessageConfirmation(title==null || title.getText()==null?null:title.getText().toString());
            if(confirm!=null && single && confirm.getText()!=null
                    && java.util.Set.of("delete","删除","刪除").contains(confirm.getText().toString().trim().toLowerCase(java.util.Locale.ROOT))) {
                flow.stage=2;
                if(!confirm.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { cancelFlow();return; }
            } else if(confirm!=null) { cancelFlow();SmsRuntime.setStatus(this,"确认框不符合单条短信删除规则，已停止");return; }
        } else if(unique(root,"android:id/button1")==null && unique(root,DeletePolicy.AOSP+":id/action_delete_message")==null
                && exact(root,flow.body).isEmpty()) {
            cancelFlow();SmsRuntime.setStatus(this,"本次单条验证码短信已从当前页面移除");return;
        }
        handler.postDelayed(scan,350);
    }
    private void cancelFlow() { flow=null; }
    @Override public void onInterrupt() {
        generation++;cancelFlow();removeChip();handler.removeCallbacks(scan);
        SmsRuntime.pendingCopy=null;copyingForDeletion=false;
    }
    @Override public void onDestroy() {
        onInterrupt();handler.removeCallbacks(expire);
        if(preferences!=null)preferences.store().unregisterOnSharedPreferenceChangeListener(preferenceChanged);
        seenNotifications.clear();pendingBody=null;copiedBody="";attemptedBody="";SmsRuntime.pendingCopy=null;copyingForDeletion=false;
        SmsRuntime.connected=false;
        SmsRuntime.setStatus(this,checkSelfPermission(android.Manifest.permission.RECEIVE_SMS)==android.content.pm.PackageManager.PERMISSION_GRANTED
                ?"页面识别已停止，新短信接收仍可用":"页面识别已停止");super.onDestroy();
    }
}
