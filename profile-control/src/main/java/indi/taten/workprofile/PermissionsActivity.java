package indi.taten.workprofile;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import indi.taten.workprofile.control.ControlSession;
import indi.taten.workprofile.control.PermissionPages;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 内部入口，不接收其他应用传入的包名或执行权限修改命令。 */
public final class PermissionsActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private List<PermissionPages.App> all = List.of();
    private final List<PermissionPages.App> visible = new ArrayList<>();
    private ControlSession session;
    private int user;
    private long serial;
    private EditText search;
    private Switch system;
    private TextView status;
    private BaseAdapter adapter;
    private boolean loading;
    private String loadError = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        user = getIntent().getIntExtra("user", -1);
        serial = getIntent().getLongExtra("serial", -1);
        session = ControlSession.get();
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(12), dp(22), dp(12)); setContentView(page);
        page.setOnApplyWindowInsetsListener((v, insets) -> {
            var safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            v.setPadding(dp(22) + safe.left, dp(12) + safe.top, dp(22) + safe.right, dp(12) + safe.bottom);
            return insets;
        });
        Button back = new Button(this); back.setText("返回工作空间工具"); back.setOnClickListener(v -> finish()); page.addView(back);
        TextView heading = text("工作应用权限", 28); heading.setTypeface(null, Typeface.BOLD); page.addView(heading);
        page.addView(text("工作空间 · " + user + "\n选择应用，在系统页面调整权限。", 15));
        search = new EditText(this); search.setSingleLine(); search.setHint("搜索应用名称或包名");
        search.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO); page.addView(search);
        system = new Switch(this); system.setText("显示系统应用"); system.setPadding(0, dp(10), 0, dp(10)); page.addView(system);
        LinearLayout actions = new LinearLayout(this); page.addView(actions);
        Button reload = new Button(this); reload.setText("刷新列表"); reload.setOnClickListener(v -> load());
        actions.addView(reload, new LinearLayout.LayoutParams(0, -2, 1));
        Button byPackage = new Button(this); byPackage.setText("按包名打开"); byPackage.setOnClickListener(v -> openPackage());
        actions.addView(byPackage, new LinearLayout.LayoutParams(0, -2, 1));
        status = text("正在读取工作应用…", 13); page.addView(status);
        ListView list = new ListView(this); page.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        adapter = new BaseAdapter() {
            @Override public int getCount() { return visible.size(); }
            @Override public Object getItem(int position) { return visible.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                TextView row = recycled instanceof TextView ? (TextView) recycled : text("", 16);
                PermissionPages.App app = visible.get(position);
                row.setText(app.name + "\n" + app.packageName); row.setPadding(0, dp(18), 0, dp(18)); return row;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            try { PermissionPages.open(this, session, user, serial, visible.get(position).packageName); }
            catch (Exception failure) { showError(ControlSession.message(failure)); }
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter(); }
            @Override public void afterTextChanged(Editable e) {}
        });
        system.setOnCheckedChangeListener((button, checked) -> filter());
        load();
    }
    private void load() {
        if (loading) return;
        if (session == null || ControlSession.get() != session) {
            loadError = "连接已结束，请返回主界面并用电脑重新激活。"; filter(); return;
        }
        loading = true; loadError = ""; filter();
        worker.execute(() -> {
            try {
                List<PermissionPages.App> loaded = PermissionPages.apps(this, session, user, serial);
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) { loading = false; all = loaded; filter(); } });
            } catch (Exception failure) {
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) {
                    loading = false; all = List.of(); loadError = ControlSession.message(failure); filter();
                } });
            }
        });
    }
    private void filter() {
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        visible.clear();
        for (PermissionPages.App app : all)
            if ((!app.system || system.isChecked()) && (app.name.toLowerCase(Locale.ROOT).contains(query)
                    || app.packageName.toLowerCase(Locale.ROOT).contains(query))) visible.add(app);
        adapter.notifyDataSetChanged();
        status.setText(loading ? "正在读取工作应用…" : !loadError.isEmpty() ? loadError
                : all.isEmpty() ? "没有可显示的工作应用，请确认工作空间已开启。"
                : visible.isEmpty() ? "没有匹配的应用；无桌面入口的应用可按包名打开。"
                : visible.size() + " 个应用 · 无桌面入口的应用可按包名打开");
    }
    private void openPackage() {
        EditText input = new EditText(this); input.setSingleLine(); input.setHint("例如 com.example.app");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        String query = search.getText().toString().trim(); if (query.contains(".")) input.setText(query);
        new AlertDialog.Builder(this).setTitle("打开工作应用权限").setMessage("仅打开当前工作空间中已安装应用的系统权限页面。")
                .setView(input).setNegativeButton("取消", null).setPositiveButton("打开", (dialog, which) -> {
                    String pkg = input.getText().toString().trim();
                    if (!pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) { showError("请输入完整的应用包名"); return; }
                    try {
                        if (session == null) throw new IllegalStateException("请先激活控制连接");
                        PermissionPages.open(this, session, user, serial, pkg);
                    } catch (Exception failure) { showError(ControlSession.message(failure)); }
                }).show();
    }
    private void showError(String message) { new AlertDialog.Builder(this).setTitle("无法打开权限页").setMessage(message).setPositiveButton("知道了", null).show(); }
    private TextView text(String value, int size) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(0xff20362e);
        view.setPadding(0, dp(7), 0, dp(7)); return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}
