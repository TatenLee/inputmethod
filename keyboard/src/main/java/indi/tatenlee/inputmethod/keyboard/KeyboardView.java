package indi.tatenlee.inputmethod.keyboard;

import android.content.Context;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import indi.tatenlee.inputmethod.core.EngineState;
import indi.tatenlee.inputmethod.core.ToolbarAction;
import indi.tatenlee.inputmethod.settings.KeyboardSettings;
import java.util.List;

/** 只绘制键盘、候选和面板；写入输入框、语音生命周期由 IME 服务管理。 */
public final class KeyboardView extends LinearLayout {
    public interface Listener {
        void onKey(String key);
        void onCandidate(int index);
        void onPage(boolean next);
        void onToolbar(ToolbarAction action);
    }
    private final KeyboardSettings settings;
    private final Listener listener;
    private final KeyboardPalette palette;
    private final TextView composing;
    private final LinearLayout toolbar, candidates, body;
    private final KeyButton previous, next;
    private EngineState state = EngineState.empty();
    private String panel = "";
    private String mode = "中文";
    private String enterLabel = "换行";
    private boolean shifted;
    private boolean privateMode;

    public KeyboardView(Context context, KeyboardSettings settings, Listener listener) {
        super(context);
        this.settings = settings; this.listener = listener;
        palette = new KeyboardPalette(getResources());
        setOrientation(VERTICAL);
        setBackgroundColor(palette.background);
        composing = text("简心 · 本地输入", 12, palette.muted);
        composing.setPadding(dp(12), 0, dp(12), 0);
        addView(composing, new LayoutParams(-1, dp(24)));
        toolbar = row();
        addView(toolbar, new LayoutParams(-1, dp(40)));
        rebuildToolbar();

        LinearLayout candidateRow = row();
        candidates = row();
        HorizontalScrollView scroller = new HorizontalScrollView(context);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.addView(candidates);
        candidateRow.addView(scroller, new LayoutParams(0, -1, 1));
        previous = key("‹", true, () -> listener.onPage(false));
        next = key("›", true, () -> listener.onPage(true));
        candidateRow.addView(previous, new LayoutParams(dp(36), -1));
        candidateRow.addView(next, new LayoutParams(dp(36), -1));
        addView(candidateRow, new LayoutParams(-1, dp(40)));
        candidateRow.setVisibility(GONE);
        body = new LinearLayout(context);
        body.setOrientation(VERTICAL);
        body.setPadding(dp(3), dp(3), dp(3), dp(3));
        addView(body, new LayoutParams(-1, dp(248)));
        showKeys();
    }

    public int backgroundColor() { return palette.background; }
    public void bodyHeight(int px) {
        if (body.getLayoutParams().height != px) body.setLayoutParams(new LayoutParams(-1, px));
    }
    public void mode(String value, boolean privacy, String action) {
        mode = value; privateMode = privacy; enterLabel = action;
        panel = ""; showKeys(); rebuildToolbar();
    }
    public void status(String value) {
        composing.setText(value.isEmpty() ? (state.preedit.isEmpty() ? "简心 · 本地输入" : state.preedit) : value);
    }
    public void state(EngineState value) {
        state = value;
        status("");
        boolean hasCandidates = !value.candidates.isEmpty();
        toolbar.setVisibility(hasCandidates ? GONE : VISIBLE);
        ((View) candidates.getParent().getParent()).setVisibility(hasCandidates ? VISIBLE : GONE);
        candidates.removeAllViews();
        for (int i = 0; i < value.candidates.size(); i++) {
            int index = i;
            TextView candidate = text(value.candidates.get(i), 21, i == 0 ? palette.accent : palette.text);
            candidate.setPadding(dp(14), 0, dp(14), 0);
            candidate.setOnClickListener(v -> listener.onCandidate(index));
            candidates.addView(candidate, new LayoutParams(-2, -1));
        }
        previous.setEnabled(value.page > 0);
        next.setEnabled(!value.lastPage);
    }

    private void rebuildToolbar() {
        toolbar.removeAllViews();
        for (ToolbarAction action : settings.toolbar()) {
            TextView button = text(action.title, 14, palette.muted);
            button.setContentDescription(action.title);
            button.setOnClickListener(v -> listener.onToolbar(action));
            button.setOnLongClickListener(v -> { listener.onToolbar(ToolbarAction.SETTINGS); return true; });
            if (privateMode && (action == ToolbarAction.VOICE || action == ToolbarAction.CLIPBOARD)) {
                button.setEnabled(false); button.setAlpha(.35f);
            }
            toolbar.addView(button, new LayoutParams(0, -1, 1));
        }
    }

    public void showKeys() {
        panel = "";
        body.removeAllViews();
        boolean numbers = mode.equals("数字");
        boolean chinese = mode.equals("中文");
        if (numbers || (chinese && settings.nineKey())) {
            addKeys(numbers ? new String[]{"1", "2", "3", "⌫"} : new String[]{"分词", "2 abc", "3 def", "⌫"});
            addKeys(numbers ? new String[]{"4", "5", "6", "-"} : new String[]{"4 ghi", "5 jkl", "6 mno", "，"});
            addKeys(numbers ? new String[]{"7", "8", "9", "."} : new String[]{"7 pqrs", "8 tuv", "9 wxyz", "。"});
        } else {
            addLetters("qwertyuiop");
            addLetters("asdfghjkl");
            addKeys(new String[]{chinese ? "分词" : "⇧", letter('z'), letter('x'), letter('c'), letter('v'), letter('b'), letter('n'), letter('m'), "⌫"});
        }
        LinearLayout row = row();
        addKey(row, "符", 1, true);
        addKey(row, mode.equals("数字") ? "中文" : "123", 1.1f, true);
        addKey(row, mode.equals("英文") ? "中" : "英", 1, true);
        addKey(row, numbers ? "0" : "空格", 3.3f, false);
        addKey(row, chinese ? "。" : ".", 1, false);
        addKey(row, enterLabel, 1.6f, true);
        body.addView(row, new LayoutParams(-1, 0, 1));
    }

    public void showEmoji() {
        if (panel.equals("emoji")) { showKeys(); return; }
        panel = "emoji"; body.removeAllViews();
        String[][] rows = {{"😀", "😂", "🥰", "😊", "😭", "🤔"}, {"👍", "🙏", "❤️", "🎉", "🌹", "✨"}, {"😅", "🤣", "😍", "😎", "🥺", "👏"}};
        for (String[] values : rows) addKeys(values);
        panelFooter();
    }

    public void showSymbols() {
        panel = "symbols"; body.removeAllViews();
        addKeys(new String[]{"，", "。", "？", "！", "、", "：", "；"});
        addKeys(new String[]{"（", "）", "《", "》", "“", "”", "…"});
        addKeys(new String[]{"@", "#", "_", "/", "\\", "-", "+"});
        panelFooter();
    }

    /** 不保存历史剪贴板。只在用户点开时读取当前项目。 */
    public void showClipboard(String content) {
        panel = "clipboard"; body.removeAllViews();
        ScrollView scroll = new ScrollView(getContext());
        TextView clip = text(content.isEmpty() ? "剪贴板里还没有文字" : content, 17, palette.text);
        clip.setGravity(Gravity.TOP | Gravity.START);
        clip.setPadding(dp(16), dp(12), dp(16), dp(12));
        scroll.addView(clip);
        body.addView(scroll, new LayoutParams(-1, 0, 1));
        LinearLayout actions = row();
        KeyButton paste = key("粘贴", false, () -> { if (!content.isEmpty()) listener.onKey("text:" + content); showKeys(); });
        paste.setEnabled(!content.isEmpty());
        actions.addView(paste, new LayoutParams(0, -1, 1));
        actions.addView(key("返回键盘", true, this::showKeys), new LayoutParams(0, -1, 1));
        body.addView(actions, new LayoutParams(-1, dp(44)));
    }

    private void panelFooter() {
        LinearLayout row = row();
        row.addView(key("返回键盘", true, this::showKeys), new LayoutParams(0, -1, 3));
        addKey(row, "⌫", 1, true);
        body.addView(row, new LayoutParams(-1, 0, 1));
    }
    private String letter(char c) { return shifted && mode.equals("英文") ? String.valueOf(Character.toUpperCase(c)) : String.valueOf(c); }
    private void addLetters(String value) {
        String[] keys = new String[value.length()];
        for (int i = 0; i < value.length(); i++) keys[i] = letter(value.charAt(i));
        addKeys(keys);
    }
    private void addKeys(String[] values) {
        LinearLayout row = row();
        for (String value : values) addKey(row, value, 1, value.equals("⌫") || value.equals("分词") || value.equals("⇧"));
        body.addView(row, new LayoutParams(-1, 0, 1));
    }
    private void addKey(LinearLayout row, String value, float weight, boolean secondary) {
        KeyButton button = key(value, secondary, () -> {
            if (value.equals("⇧")) { shifted = !shifted; showKeys(); }
            else if (value.equals("符")) showSymbols();
            else listener.onKey(value);
        });
        if (value.equals("⌫")) { button.setContentDescription("删除"); button.enableRepeat(); }
        LayoutParams params = new LayoutParams(0, -1, weight);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        row.addView(button, params);
    }
    private KeyButton key(String text, boolean secondary, Runnable action) {
        KeyButton button = new KeyButton(getContext(), text, palette, secondary);
        button.setOnClickListener(v -> {
            if (settings.haptic()) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            action.run();
        });
        return button;
    }
    private LinearLayout row() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }
    private TextView text(String text, int size, int color) {
        TextView view = new TextView(getContext());
        view.setText(text); view.setTextSize(size); view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        return view;
    }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
