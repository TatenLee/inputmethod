package indi.tatenlee.inputmethod.settings;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import indi.tatenlee.inputmethod.core.ToolbarAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;

/** 设置按用户任务排列：启用 → 输入习惯 → 布局 → 工具栏 → 语音。 */
public final class SettingsActivity extends Activity {
    private KeyboardSettings settings;
    private LinearLayout page;
    private TextView microphone;
    private static final int INK = 0xff20362e, MUTED = 0xff62766b, GREEN = 0xff176b58;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        settings = new KeyboardSettings(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xfff5f7f4);
        page = vertical(); page.setPadding(dp(22), dp(20), dp(22), dp(28));
        scroll.addView(page); WindowPadding.setContent(this, scroll);
        TextView eyebrow = text("JIANXIN / 简心", 12, GREEN); page.addView(eyebrow);
        TextView title = text("简心输入法", 32, INK); title.setTypeface(null, Typeface.BOLD); page.addView(title);
        TextView description = text("顺手的键盘，留给自己的空间。", 15, MUTED);
        description.setPadding(0, dp(8), 0, dp(24)); page.addView(description);

        LinearLayout setup = card("开始使用");
        setup.addView(text("先启用简心，再将它选为当前输入法。", 14, MUTED));
        button(setup, "① 启用简心输入法", () -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        button(setup, "② 选择当前输入法", () -> getSystemService(InputMethodManager.class).showInputMethodPicker());
        EditText preview = new EditText(this);
        preview.setId(indi.tatenlee.inputmethod.R.id.typing_preview);
        preview.setHint("点这里试打：你好，简心"); preview.setTextSize(17);
        preview.setSingleLine(false); preview.setMinLines(2);
        setup.addView(preview, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout habits = card("输入习惯");
        toggle(habits, "中文使用九键", settings.nineKey(), settings::nineKey);
        habits.addView(text("关闭时使用全拼键盘；英文保持 26 键。", 13, MUTED));
        toggle(habits, "按键振动", settings.haptic(), settings::haptic);

        LinearLayout layout = card("键盘与底部横条");
        layout.addView(text("保留系统导航区，在上方额外留出你觉得舒服的距离。横屏与竖屏分别保存。", 14, MUTED));
        slider(layout, "竖屏键盘高度", 140, 360, settings.height(false), v -> settings.height(false, v));
        slider(layout, "横屏键盘高度", 140, 260, settings.height(true), v -> settings.height(true, v));
        slider(layout, "竖屏底部额外留白", 0, 64, settings.extraBottom(false), v -> settings.extraBottom(false, v));
        slider(layout, "横屏底部额外留白", 0, 64, settings.extraBottom(true), v -> settings.extraBottom(true, v));
        layout.addView(text("屏幕空间不足时，键盘会自动缩小。设置后重新打开键盘即可生效。", 12, MUTED));

        LinearLayout toolbar = card("常用按钮的顺序");
        toolbar.addView(text("表情、剪贴板、语音和设置，按你的习惯排列。", 14, MUTED));
        button(toolbar, "调整工具栏", this::showToolbarEditor);

        LinearLayout voice = card("离线语音");
        microphone = text("", 14, MUTED); voice.addView(microphone);
        voice.addView(text("点键盘上的「语音」开始，再点一次结束。支持中、英、粤等语言；每次最长 30 秒，录音不会保存。", 14, MUTED));
        button(voice, "允许使用麦克风", this::requestMicrophone);

        LinearLayout about = card("关于简心");
        about.addView(text("0.1.1 · ARM64 · 无桌面图标\n独立界面 · RIME 输入引擎 · 本地 SenseVoice 语音\nindi.tatenlee.inputmethod", 14, MUTED));
        about.addView(text("应用没有联网权限。剪贴板只在点开时读取，不保存历史。密码输入框默认禁用语音、剪贴板与学习。", 13, MUTED));
        button(about, "开源组件与许可", this::showLicenses);
        if (getIntent().getBooleanExtra("request_microphone", false)) requestMicrophone();
    }

    @Override protected void onResume() { super.onResume(); updateMicrophone(); }

    private void updateMicrophone() {
        if (microphone != null) microphone.setText(checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                ? "麦克风已允许 · 识别在手机上完成" : "首次使用需要允许麦克风");
    }
    private void requestMicrophone() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this).setMessage("麦克风已允许。返回输入框，点键盘上的「语音」即可使用。").setPositiveButton("知道了", null).show();
        } else requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 7);
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        updateMicrophone();
        if (requestCode == 7 && grants.length > 0 && grants[0] != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this).setMessage("暂未获得麦克风权限。仍可正常打字；需要语音时可在系统应用权限中开启麦克风。")
                    .setPositiveButton("知道了", null).show();
        }
    }

    private void showToolbarEditor() {
        LinearLayout list = vertical(); list.setPadding(dp(16), dp(8), dp(16), dp(8));
        List<ToolbarAction> order = new ArrayList<>(settings.toolbar());
        renderToolbarEditor(list, order);
        new AlertDialog.Builder(this).setTitle("长按拖动，或点箭头调整")
                .setView(list).setPositiveButton("完成", null).show();
    }
    private void renderToolbarEditor(LinearLayout list, List<ToolbarAction> order) {
        list.removeAllViews();
        for (int index = 0; index < order.size(); index++) {
            int position = index;
            ToolbarAction action = order.get(index);
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = text("☰   " + action.title, 17, INK);
            label.setPadding(dp(6), 0, 0, 0);
            row.addView(label, new LinearLayout.LayoutParams(0, dp(56), 1));
            label.setGravity(Gravity.CENTER_VERTICAL);
            row.setOnLongClickListener(v -> v.startDragAndDrop(ClipData.newPlainText("toolbar", action.id), new View.DragShadowBuilder(v), action, 0));
            row.setOnDragListener((v, event) -> {
                if (!(event.getLocalState() instanceof ToolbarAction)) return false;
                if (event.getAction() == DragEvent.ACTION_DRAG_ENTERED) v.setBackgroundColor(0xffdcece4);
                if (event.getAction() == DragEvent.ACTION_DRAG_EXITED || event.getAction() == DragEvent.ACTION_DRAG_ENDED) v.setBackgroundColor(Color.TRANSPARENT);
                if (event.getAction() == DragEvent.ACTION_DROP) {
                    ToolbarAction moved = (ToolbarAction) event.getLocalState();
                    order.remove(moved); order.add(position, moved);
                    settings.toolbar(order); renderToolbarEditor(list, order);
                }
                return true;
            });
            Button up = new Button(this); up.setText("↑"); up.setContentDescription("向前移动" + action.title);
            up.setEnabled(index > 0); up.setOnClickListener(v -> { Collections.swap(order, position, position - 1); settings.toolbar(order); renderToolbarEditor(list, order); });
            Button down = new Button(this); down.setText("↓"); down.setContentDescription("向后移动" + action.title);
            down.setEnabled(index < order.size() - 1); down.setOnClickListener(v -> { Collections.swap(order, position, position + 1); settings.toolbar(order); renderToolbarEditor(list, order); });
            row.addView(up, new LinearLayout.LayoutParams(dp(52), dp(48)));
            row.addView(down, new LinearLayout.LayoutParams(dp(52), dp(48)));
            list.addView(row);
        }
    }

    private void showLicenses() {
        Intent intent = new Intent(this, LicensesActivity.class);
        startActivity(intent);
    }
    private LinearLayout vertical() { LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); return box; }
    private LinearLayout card(String title) {
        LinearLayout box = vertical(); box.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable shape = new GradientDrawable(); shape.setColor(Color.WHITE); shape.setCornerRadius(dp(18)); box.setBackground(shape);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(14); page.addView(box, params);
        TextView label = text(title, 19, INK); label.setTypeface(null, Typeface.BOLD); label.setPadding(0, 0, 0, dp(10)); box.addView(label);
        return box;
    }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setLineSpacing(dp(3), 1); return view;
    }
    private void button(LinearLayout parent, String title, Runnable action) {
        Button button = new Button(this); button.setText(title); button.setTextColor(GREEN); button.setAllCaps(false);
        button.setOnClickListener(v -> action.run()); parent.addView(button, new LinearLayout.LayoutParams(-1, -2));
    }
    private void slider(LinearLayout parent, String label, int min, int max, int value, IntConsumer change) {
        TextView title = text(label + " · " + value + " dp", 14, INK); title.setPadding(0, dp(16), 0, 0); parent.addView(title);
        SeekBar seek = new SeekBar(this); seek.setMax(max - min); seek.setProgress(Math.max(0, Math.min(max - min, value - min)));
        seek.setContentDescription(label); parent.addView(seek, new LinearLayout.LayoutParams(-1, dp(40)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                title.setText(label + " · " + (progress + min) + " dp"); if (fromUser) change.accept(progress + min);
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
    }
    private void toggle(LinearLayout parent, String label, boolean value, java.util.function.Consumer<Boolean> change) {
        Switch toggle = new Switch(this); toggle.setText(label); toggle.setTextSize(16); toggle.setTextColor(INK); toggle.setChecked(value);
        toggle.setPadding(0, dp(12), 0, dp(12)); toggle.setOnCheckedChangeListener((v, enabled) -> change.accept(enabled));
        parent.addView(toggle, new LinearLayout.LayoutParams(-1, -2));
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
