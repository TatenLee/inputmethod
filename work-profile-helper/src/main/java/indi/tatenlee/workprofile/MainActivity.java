package indi.tatenlee.workprofile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.UserManager;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 只打开同一空间中系统已导出的页面；不申请权限、不执行 shell、不直接写系统配置。 */
public final class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(24), dp(24), dp(24));
        content.setBackgroundColor(Color.rgb(247, 249, 246));
        scroll.addView(content);
        setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            var safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        boolean work = getSystemService(UserManager.class).isManagedProfile();
        text(content, "简心·空间设置", 28);
        text(content, work ? "当前：工作空间" : "当前：个人空间", 18);
        text(content, work
                ? "从这里打开此空间内的系统导航页面，查找「导航栏 → 隐藏」。如果系统仍未提供该选项，下方会保留键盘试打入口。"
                : "要调整工作空间，请在 Shelter 中把本工具克隆到工作空间，再打开带公文包标记的副本。", 16);
        button(content, "打开手势导航设置", "com.android.settings.Settings$GestureNavigationSettingsActivity");
        button(content, "打开 Moto 系统导航", "com.motorola.settings.gestures.SystemNavigationGestureActivity");
        button(content, "打开系统导航模式", "com.android.settings.Settings$NavigationModeSettingsActivity");
        text(content, "检查键盘底部", 20);
        EditText preview = new EditText(this);
        preview.setHint("点这里弹出键盘，检查小白条");
        preview.setTextSize(18);
        preview.setMinLines(2);
        preview.setSaveEnabled(false);
        preview.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
        content.addView(preview, new LinearLayout.LayoutParams(-1, -2));
        text(content, "本工具没有联网或系统修改权限，试打内容不保存。页面是否开放由手机系统决定。", 14);
    }

    private void button(LinearLayout parent, String label, String activity) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(view -> {
            try {
                startActivity(new Intent().setComponent(new ComponentName("com.android.settings", activity)));
            } catch (android.content.ActivityNotFoundException | SecurityException exception) {
                new AlertDialog.Builder(this).setTitle("系统没有开放这个页面")
                        .setMessage("可以尝试另一个入口；本工具不会绕过系统权限限制。")
                        .setPositiveButton("知道了", null).show();
            }
        });
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, dp(60));
        layout.topMargin = dp(10);
        parent.addView(button, layout);
    }

    private void text(LinearLayout parent, String value, int size) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(Color.rgb(30, 55, 45));
        text.setPadding(0, dp(12), 0, dp(12));
        parent.addView(text, new LinearLayout.LayoutParams(-1, -2));
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
