package indi.taten.workprofile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.UserManager;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import indi.taten.workprofile.control.ControlSession;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout page;
    private boolean busy;
    private boolean registered;
    private int request;
    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { if (!busy) refresh(); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        page = column(); page.setPadding(dp(22), dp(20), dp(22), dp(28));
        scroll.addView(page); scroll.setFillViewport(true); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            var safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            v.setPadding(safe.left, safe.top, safe.right, safe.bottom); return insets;
        });
    }
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag") // 旧 API 分支无此标志；广播只触发只读刷新。
    @Override protected void onStart() {
        super.onStart();
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(changes, new IntentFilter(ControlSession.CHANGED), RECEIVER_NOT_EXPORTED);
        else registerReceiver(changes, new IntentFilter(ControlSession.CHANGED));
        registered = true; refresh();
    }
    @Override protected void onStop() { if (registered) unregisterReceiver(changes); registered = false; super.onStop(); }
    @Override protected void onDestroy() { request++; worker.shutdownNow(); super.onDestroy(); }
    private void refresh() {
        int version = ++request;
        ControlSession session = ControlSession.get();
        if (session == null || !session.ready()) { render(session, null, ""); return; }
        worker.execute(() -> {
            try {
                List<ControlSession.Profile> profiles = session.profiles();
                runOnUiThread(() -> { if (!isFinishing() && version == request) render(session, profiles, ""); });
            } catch (Exception error) {
                runOnUiThread(() -> { if (!isFinishing() && version == request) render(session, null, ControlSession.message(error)); });
            }
        });
    }
    private void render(ControlSession session, List<ControlSession.Profile> profiles, String error) {
        page.removeAllViews();
        label(page, "JIANXIN / DEVICE TOOLS", 12, 0xff176b58);
        TextView title = label(page, "工作空间", 32, 0xff20362e); title.setTypeface(null, Typeface.BOLD);
        label(page, "调整导航横条，管理工作应用权限。", 15, 0xff62766b);
        LinearLayout status = card("控制连接");
        boolean work = getSystemService(UserManager.class).isManagedProfile();
        if (work) {
            label(status, "请打开主空间中的本工具。它会自动找到关联的工作空间。", 16, 0xff20362e);
        } else if (session == null) {
            label(status, "尚未激活", 19, 0xff20362e);
            label(status, "用 USB 连接已授权的电脑，在工具包目录运行下方命令。激活后可拔线，在这里操作。", 14, 0xff62766b);
            button(status, "复制电脑激活命令", () -> {
                getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("激活工作空间工具", "python3 activate-profile-control.py"));
                android.widget.Toast.makeText(this, "已复制电脑端命令", android.widget.Toast.LENGTH_SHORT).show();
            });
            label(status, "重启、强行停止或系统结束控制进程后，需要重新激活。", 13, 0xff62766b);
            if (!ControlSession.lastError.isEmpty()) label(status, ControlSession.lastError, 13, 0xffa04030);
        } else if (!session.ready()) {
            label(status, "已获得本次调试授权，等待电脑完成连接…", 16, 0xff20362e);
            button(status, "刷新连接", this::refresh);
        } else {
            label(status, "已激活 · 可以操作", 19, 0xff176b58);
            button(status, "刷新工作空间", this::refresh);
            button(status, "结束本次控制连接", session::stop);
        }
        if (!error.isEmpty()) label(card("无法读取"), error, 15, 0xffa04030);
        if (profiles != null) {
            if (profiles.isEmpty()) label(card("没有工作空间"), "未找到此用户关联的工作空间。请先在 Shelter 或系统中创建并开启。", 15, 0xff62766b);
            for (ControlSession.Profile profile : profiles) {
                LinearLayout box = card("工作空间 · " + profile.user);
                button(box, "工作应用权限", () -> startActivity(new Intent(this, PermissionsActivity.class)
                        .putExtra("user", profile.user).putExtra("serial", profile.serial)));
                if (!profile.issue.isEmpty()) { label(box, profile.issue, 15, 0xff62766b); continue; }
                label(box, profile.hidden ? "导航横条：已隐藏" : "导航横条：显示", 20, 0xff20362e);
                button(box, profile.hidden ? "显示横条" : "隐藏横条", () -> apply(session, profile, !profile.hidden, false));
                if (profile.hasBackup) button(box, "恢复首次修改前的状态", () -> apply(session, profile, false, true));
                label(box, "修改后，请在工作应用里收起并重新打开键盘检查。", 13, 0xff62766b);
            }
        }
        LinearLayout check = card("检查键盘");
        label(check, work ? "当前为工作空间，可在这里检查。" : "这里是主空间；工作空间的效果请在带公文包标记的应用中检查。", 14, 0xff62766b);
        EditText preview = new EditText(this); preview.setHint("点这里试打"); preview.setMinLines(2);
        preview.setSaveEnabled(false); preview.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
        check.addView(preview);
        label(page, "0.2.0 · 无联网权限\n横条功能需要系统内置隐藏配置。权限入口已在 Moto X40 / Android 16 验证，系统更新后可能需要适配。", 12, 0xff62766b);
    }
    private void apply(ControlSession session, ControlSession.Profile profile, boolean hidden, boolean restore) {
        if (busy) return; busy = true; request++;
        setButtonsEnabled(page, false);
        worker.execute(() -> {
            String error = "";
            try { session.change(profile, hidden, restore); } catch (Exception failure) { error = ControlSession.message(failure); }
            String result = error;
            runOnUiThread(() -> {
                busy = false;
                if (isFinishing()) return;
                refresh();
                if (!result.isEmpty()) new AlertDialog.Builder(this).setTitle("未完成修改").setMessage(result).setPositiveButton("知道了", null).show();
                else android.widget.Toast.makeText(this, "已写入并核对系统状态", android.widget.Toast.LENGTH_SHORT).show();
            });
        });
    }
    private void setButtonsEnabled(android.view.ViewGroup group, boolean enabled) {
        for (int i=0;i<group.getChildCount();i++) {
            var v=group.getChildAt(i); if (v instanceof Button) v.setEnabled(enabled);
            if (v instanceof android.view.ViewGroup) setButtonsEnabled((android.view.ViewGroup)v, enabled);
        }
    }
    private LinearLayout column() { LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); return box; }
    private LinearLayout card(String title) {
        LinearLayout box = column(); box.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(0xffffffff); bg.setCornerRadius(dp(18)); box.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.topMargin=dp(18); page.addView(box,lp);
        TextView heading=label(box,title,17,0xff20362e); heading.setTypeface(null,Typeface.BOLD); return box;
    }
    private TextView label(LinearLayout box,String value,int size,int color) {
        TextView text=new TextView(this);text.setText(value);text.setTextSize(size);text.setTextColor(color);text.setPadding(0,dp(7),0,dp(7));
        box.addView(text,new LinearLayout.LayoutParams(-1,-2));return text;
    }
    private void button(LinearLayout box,String title,Runnable action) {
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextColor(0xff176b58);b.setOnClickListener(v->action.run());box.addView(b,new LinearLayout.LayoutParams(-1,dp(54)));
    }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
}
