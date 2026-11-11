package indi.taten.smscode;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import indi.taten.smscode.core.DeletePolicy;
import indi.taten.smscode.core.OtpExtractor;
import java.util.LinkedHashMap;

public final class MainActivity extends Activity {
    private SmsPreferences preferences;
    private LinearLayout page;
    private TextView status;
    private EditText sample;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable update = this::updateStatus;
    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { updateStatus(); }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); preferences = new SmsPreferences(this);
        ScrollView scroll = new ScrollView(this); page = column();
        page.setPadding(dp(22), dp(18), dp(22), dp(28)); scroll.addView(page); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets) -> {
            var safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
            v.setPadding(safe.left,safe.top,safe.right,safe.bottom);
            if(insets.isVisible(WindowInsets.Type.ime()) && getCurrentFocus() instanceof EditText) {
                View focused=getCurrentFocus();focused.post(()->focused.requestRectangleOnScreen(new android.graphics.Rect(0,0,focused.getWidth(),focused.getHeight()),true));
            }
            return insets;
        });
        render();
        if(Intent.ACTION_SEND.equals(getIntent().getAction()) && "text/plain".equals(getIntent().getType())) {
            CharSequence shared = getIntent().getCharSequenceExtra(Intent.EXTRA_TEXT);
            if(shared!=null) sample.setText(shared.length()>4096 ? "文本过长，请粘贴单条短信。" : shared);
        }
    }
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag") // 旧 API 分支无此标志；广播只刷新本进程状态。
    @Override protected void onStart() {
        super.onStart();
        if(android.os.Build.VERSION.SDK_INT>=33) registerReceiver(changes,new IntentFilter(SmsRuntime.CHANGED),RECEIVER_NOT_EXPORTED);
        else registerReceiver(changes,new IntentFilter(SmsRuntime.CHANGED));
        updateStatus();
    }
    @Override protected void onStop() { unregisterReceiver(changes);handler.removeCallbacks(update);super.onStop(); }
    private void updateStatus() {
        if(status!=null) {
            String code=SmsRuntime.recentCode();
            boolean sms=checkSelfPermission(android.Manifest.permission.RECEIVE_SMS)==android.content.pm.PackageManager.PERMISSION_GRANTED;
            status.setText("短信接收："+(sms?"已授权":"未授权")+" · 无障碍："+(SmsRuntime.connected?"已连接":"未连接")+"\n"+SmsRuntime.status
                    +(code.isEmpty()?"":"\n最近识别："+code+"（仅短暂显示）"));
        }
        handler.removeCallbacks(update);handler.postDelayed(update,5000);
    }
    private String appName(String pkg) {
        try {return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString();}
        catch(android.content.pm.PackageManager.NameNotFoundException ignored) {return pkg.isEmpty()?"未选择短信 App":pkg;}
    }
    private void render() {
        page.removeAllViews();
        label(page,"JIANXIN / LOCAL TOOLS",12,0xff176b58);
        TextView title=label(page,"简心验证码",32,0xff20362e);title.setTypeface(null,Typeface.BOLD);
        label(page,"读到验证码，就少切换一次。",15,0xff62766b);
        LinearLayout connection=card("连接短信 App");
        label(connection,appName(preferences.smsPackage()),19,0xff20362e);
        button(connection,"选择短信 App",this::chooseSmsApp);
        boolean smsGranted=checkSelfPermission(android.Manifest.permission.RECEIVE_SMS)==android.content.pm.PackageManager.PERMISSION_GRANTED;
        button(connection,smsGranted?"接收短信权限：已允许":"允许接收新短信（后台复制）",()-> {
            if(checkSelfPermission(android.Manifest.permission.RECEIVE_SMS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{android.Manifest.permission.RECEIVE_SMS},42);
            else startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));
        });
        label(connection,"接收权限只用于新到的短信，不读取历史、不发送短信。只需要后台复制时，可不开无障碍。",14,0xff62766b);
        button(connection,"打开无障碍设置",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        label(connection,"需要页面识别或辅助删除时，再开启无障碍。它只处理上方选定 App 的通知和短信页面，保留原默认短信 App。",14,0xff62766b);
        status=label(connection,"",14,0xff176b58);updateStatus();
        LinearLayout copy=card("复制验证码");
        Switch automatic=new Switch(this);automatic.setText("自动识别并复制");automatic.setChecked(preferences.autoCopy());
        automatic.setPadding(0,dp(10),0,dp(10));copy.addView(automatic);
        automatic.setOnCheckedChangeListener((v,value)->preferences.autoCopy(value));
        label(copy,"已授权短信接收时直接识别新短信；未授权时通过无障碍识别通知或短信页面。锁屏时不处理。",14,0xff62766b);
        LinearLayout deletion=card("复制后的短信");
        boolean supported=DeletePolicy.AOSP.equals(preferences.smsPackage());
        RadioGroup modes=new RadioGroup(this);deletion.addView(modes);
        String[] names={"保留短信","显示「删除此条验证码」按钮","自动删除新验证码（打开短信后）"};
        for(int i=0;i<names.length;i++) {
            RadioButton option=new RadioButton(this);option.setId(200+i);option.setText(names[i]);
            option.setEnabled(i==0 || supported);option.setPadding(0,dp(7),0,dp(7));modes.addView(option);
        }
        modes.check(200+preferences.deletionMode());
        modes.setOnCheckedChangeListener((group,id)-> {
            int selected=id-200;
            if(selected==preferences.deletionMode())return;
            if(selected==SmsPreferences.AUTO_DELETE) {
                new AlertDialog.Builder(this).setTitle("开启单条短信自动删除？")
                    .setMessage("只处理最近收到的新短信或可读通知，且必须与当前会话、正文匹配。需要打开并解锁短信页面；清理前会短暂显示复制确认窗。删除后不能由本工具恢复。无法匹配时会退回删除按钮。")
                    .setPositiveButton("开启",(d,w)->preferences.deletionMode(selected))
                    .setNegativeButton("取消",(d,w)->group.check(200+preferences.deletionMode()))
                    .setOnCancelListener(d->group.check(200+preferences.deletionMode())).show();
            } else preferences.deletionMode(selected);
        });
        label(deletion,supported?"适配 AOSP Messaging 的单条短信删除界面。清理前会短暂显示复制确认窗；遇到不匹配的界面会停止。厂商修改版需要实机验收。":"此短信 App 尚未适配删除界面，当前只支持复制。",14,0xff62766b);
        LinearLayout local=card("本地试一下");
        label(local,"粘贴或从短信 App 分享一条文本，检查识别结果。这里不会删除短信。",14,0xff62766b);
        sample=new EditText(this);sample.setHint("例如：您的验证码为 123456，请勿告知他人。");sample.setMinLines(2);
        sample.setSaveEnabled(false);sample.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        sample.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        sample.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4096)});local.addView(sample);
        button(local,"提取并复制",()-> {
            String code=OtpExtractor.extract(sample.getText().toString());
            if(code==null) new AlertDialog.Builder(this).setTitle("未找到明确验证码").setMessage("请提供包含「验证码」或 verification code 的完整单条短信；多个不同验证码不会自动选择。").setPositiveButton("知道了",null).show();
            else {SmsRuntime.copy(this,code,"手动文本");sample.setText("");updateStatus();}
        });
        label(page,"0.1.0 · 无联网权限 · 无 Root\n不保存短信或验证码历史；规则随 APK 更新。短信与剪贴板的系统保留策略由 Android 和原短信 App 管理。",12,0xff62766b);
    }
    private void chooseSmsApp() {
        LinkedHashMap<String,String> apps=new LinkedHashMap<>();
        for(ResolveInfo item:getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:")),0)) {
            String pkg=item.activityInfo.packageName;apps.put(pkg,appName(pkg));
        }
        if(apps.isEmpty()) {new AlertDialog.Builder(this).setMessage("此空间未找到可用短信 App，请先安装或启用。").setPositiveButton("知道了",null).show();return;}
        String[] packages=apps.keySet().toArray(new String[0]);String[] labels=apps.values().toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("选择要处理的短信 App").setItems(labels,(d,w)->{preferences.smsPackage(packages[w]);render();}).show();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==42)render();
    }
    private LinearLayout column(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);return box;}
    private LinearLayout card(String title) {
        LinearLayout box=column();box.setPadding(dp(18),dp(16),dp(18),dp(16));
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xffffffff);bg.setCornerRadius(dp(18));box.setBackground(bg);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(18);page.addView(box,lp);
        TextView heading=label(box,title,17,0xff20362e);heading.setTypeface(null,Typeface.BOLD);return box;
    }
    private TextView label(LinearLayout box,String value,int size,int color) {
        TextView text=new TextView(this);text.setText(value);text.setTextSize(size);text.setTextColor(color);text.setPadding(0,dp(7),0,dp(7));box.addView(text,new LinearLayout.LayoutParams(-1,-2));return text;
    }
    private void button(LinearLayout box,String title,Runnable action) {
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextColor(0xff176b58);b.setOnClickListener(v->action.run());box.addView(b,new LinearLayout.LayoutParams(-1,dp(54)));
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
