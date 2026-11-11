package indi.tatenlee.inputmethod.ime;

import android.Manifest;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import indi.tatenlee.inputmethod.core.EngineState;
import indi.tatenlee.inputmethod.core.SessionGuard;
import indi.tatenlee.inputmethod.core.ToolbarAction;
import indi.tatenlee.inputmethod.engine.RimeEngine;
import indi.tatenlee.inputmethod.keyboard.KeyboardView;
import indi.tatenlee.inputmethod.settings.KeyboardSettings;
import indi.tatenlee.inputmethod.settings.SettingsActivity;
import indi.tatenlee.inputmethod.speech.OfflineSpeech;

/** Android 系统入口：连接独立的界面、RIME、语音和设置模块。 */
public final class JianxinInputMethodService extends InputMethodService implements KeyboardView.Listener, RimeEngine.Listener, OfflineSpeech.Listener {
    private final SessionGuard session = new SessionGuard();
    private KeyboardSettings settings;
    private RimeEngine engine;
    private OfflineSpeech speech;
    private KeyboardView keyboard;
    private ImeLayout layout;
    private String mode = "中文";
    private boolean privateMode;
    private EngineState state = EngineState.empty();
    private boolean rotating;
    private EditorInfo activeEditor;
    private String speechStatus = "";

    @Override public void onCreate() {
        super.onCreate();
        settings = new KeyboardSettings(this);
        engine = new RimeEngine(this, this);
        speech = new OfflineSpeech(this, this);
    }

    @Override public View onCreateInputView() {
        keyboard = new KeyboardView(this, settings, this);
        keyboard.mode(mode, privateMode, EditorPolicy.enterLabel(getCurrentInputEditorInfo()));
        keyboard.state(state);
        if (!engine.ready()) keyboard.status("首次使用，正在准备词库…");
        layout = new ImeLayout(this, keyboard, settings);
        Window window = getWindow().getWindow();
        if (window != null) {
            if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false);
            if (Build.VERSION.SDK_INT >= 29) window.setNavigationBarContrastEnforced(false);
            window.setNavigationBarColor(keyboard.backgroundColor());
            boolean light = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
            window.getDecorView().setSystemUiVisibility(light ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
        }
        return layout;
    }

    @Override public boolean onEvaluateFullscreenMode() { return false; }

    @Override public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting);
        speech.cancel();
        boolean sameEditor = rotating && activeEditor != null && info.fieldId == activeEditor.fieldId
                && java.util.Objects.equals(info.packageName, activeEditor.packageName)
                && info.inputType == activeEditor.inputType;
        if (!sameEditor) {
            session.advance();
            privateMode = EditorPolicy.privateInput(info);
            mode = EditorPolicy.mode(info);
            state = EngineState.empty();
            engine.configure(session.current(), settings.nineKey(), privateMode);
        } else engine.command(session.current(), RimeEngine.QUERY, 0);
        activeEditor = info;
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (keyboard != null) {
            keyboard.mode(mode, privateMode, EditorPolicy.enterLabel(info));
            keyboard.state(state);
            if (!engine.ready()) keyboard.status("首次使用，正在准备词库…");
            layout.updateSize(); layout.requestApplyInsets();
        }
        engine.command(session.current(), RimeEngine.QUERY, 0);
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        // 系统可能在 super 中重启输入会话；保留 RIME 组合输入，重建全部尺寸和触摸区域。
        rotating = true;
        speech.cancel();
        try { super.onConfigurationChanged(configuration); }
        finally { rotating = false; }
        // super 会重新调用 onCreateInputView；再次替换会让刚显示的窗口丢失首帧。
        if (layout != null) { layout.updateSize(); layout.requestApplyInsets(); }
        engine.command(session.current(), RimeEngine.QUERY, 0);
    }

    @Override public void onFinishInputView(boolean finishingInput) {
        speech.cancel();
        if (!rotating) {
            session.advance();
            engine.command(session.current(), RimeEngine.CLEAR, 0);
            InputConnection connection = getCurrentInputConnection();
            if (connection != null) connection.finishComposingText();
        }
        super.onFinishInputView(finishingInput);
    }

    @Override public void onFinishInput() {
        speech.cancel();
        if (!rotating) { session.advance(); activeEditor = null; state = EngineState.empty(); engine.command(session.current(), RimeEngine.CLEAR, 0); }
        super.onFinishInput();
    }

    @Override public void onUpdateSelection(int oldStart, int oldEnd, int newStart, int newEnd, int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldStart, oldEnd, newStart, newEnd, candidatesStart, candidatesEnd);
        if (!state.preedit.isEmpty() && (newStart != candidatesEnd || newEnd != candidatesEnd)) {
            speech.cancel(); session.advance();
            engine.command(session.current(), RimeEngine.CLEAR, 0);
            InputConnection connection = getCurrentInputConnection();
            if (connection != null) connection.finishComposingText();
        }
    }

    @Override public void onComputeInsets(Insets out) {
        super.onComputeInsets(out);
        if (layout == null) return;
        int[] position = new int[2]; layout.getLocationInWindow(position);
        out.contentTopInsets = out.visibleTopInsets = position[1];
        out.touchableInsets = Insets.TOUCHABLE_INSETS_REGION;
        out.touchableRegion.set(position[0], position[1], position[0] + layout.getWidth(),
                position[1] + layout.getHeight() - layout.navigationBottom());
    }

    @Override public void onKey(String key) {
        if (speech.busy()) { speech.cancel(); keyboard.status(""); }
        if (key.equals("123") || key.equals("中文") || key.equals("中") || key.equals("英")) {
            engine.text(session.current(), "");
            mode = key.equals("123") ? "数字" : key.equals("英") ? "英文" : "中文";
            keyboard.mode(mode, privateMode, EditorPolicy.enterLabel(getCurrentInputEditorInfo()));
        } else if (key.equals("⌫")) engine.command(session.current(), RimeEngine.KEY, 0xff08);
        else if (key.equals(EditorPolicy.enterLabel(getCurrentInputEditorInfo()))) engine.command(session.current(), RimeEngine.KEY, 0xff0d);
        else if (key.equals("空格")) engine.command(session.current(), RimeEngine.KEY, 32);
        else if (key.startsWith("text:")) engine.text(session.current(), key.substring(5));
        else if (mode.equals("中文") && (key.equals("分词") || key.matches("[a-z]") || key.matches("[2-9] [a-z]+"))) {
            engine.command(session.current(), RimeEngine.KEY, key.equals("分词") ? 39 : key.charAt(0));
        } else engine.text(session.current(), key);
    }

    @Override public void onCandidate(int index) { engine.command(session.current(), RimeEngine.SELECT, index); }
    @Override public void onPage(boolean next) { engine.command(session.current(), RimeEngine.KEY, next ? 0xff56 : 0xff55); }

    @Override public void onToolbar(ToolbarAction action) {
        switch (action) {
            case EMOJI -> keyboard.showEmoji();
            case SETTINGS -> openSettings(false);
            case CLIPBOARD -> {
                if (privateMode) return;
                ClipboardManager clipboard = getSystemService(ClipboardManager.class);
                var clip = clipboard.getPrimaryClip();
                String content = "";
                if (clip != null && clip.getItemCount() > 0) {
                    CharSequence text = clip.getItemAt(0).getText();
                    if (text != null) content = text.toString();
                }
                keyboard.showClipboard(content);
            }
            case VOICE -> {
                if (privateMode) return;
                if (speech.busy()) { speech.finish(); return; }
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { openSettings(true); return; }
                engine.text(session.current(), "");
                speech.start(session.current());
            }
        }
    }

    private void openSettings(boolean microphone) {
        Intent intent = new Intent(this, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("request_microphone", microphone);
        startActivity(intent);
    }

    @Override public void onState(long token, EngineState value) {
        if (!session.accepts(token)) return;
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return;
        connection.beginBatchEdit();
        try {
            if (!value.commit.isEmpty()) connection.commitText(value.commit, 1);
            if (!value.preedit.isEmpty()) connection.setComposingText(value.preedit, 1);
            else if (!state.preedit.isEmpty() && value.commit.isEmpty()) connection.setComposingText("", 1);
            if (value.preedit.isEmpty()) connection.finishComposingText();
        } finally { connection.endBatchEdit(); }
        state = value;
        if (keyboard != null) {
            keyboard.state(value);
            if (speech.busy()) keyboard.status(speechStatus);
        }
    }
    @Override public void onError(long token, String message) { if (session.accepts(token) && keyboard != null) keyboard.status(message); }
    @Override public void onUnhandled(long token, int key) {
        if (!session.accepts(token)) return;
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return;
        if (key == 0xff08) {
            CharSequence selected = connection.getSelectedText(0);
            if (selected != null && selected.length() > 0) connection.commitText("", 1);
            else {
                CharSequence before = connection.getTextBeforeCursor(128, 0);
                if (before != null && before.length() > 0) {
                    android.icu.text.BreakIterator boundaries = android.icu.text.BreakIterator.getCharacterInstance();
                    boundaries.setText(before.toString());
                    int boundary = boundaries.preceding(before.length());
                    connection.deleteSurroundingText(before.length() - Math.max(0, boundary), 0);
                }
            }
        } else if (key == 0xff0d) {
            EditorInfo info = getCurrentInputEditorInfo();
            int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
            if ((info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0
                    && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) connection.performEditorAction(action);
            else connection.commitText("\n", 1);
        } else if (key < 0xff00) connection.commitText(new String(Character.toChars(key)), 1);
    }
    @Override public void onStatus(String message, boolean busy) {
        speechStatus = message;
        if (keyboard != null) keyboard.status(message);
    }
    @Override public void onResult(long token, String text) { if (session.accepts(token)) engine.text(token, text); }

    @Override public void onDestroy() { speech.close(); engine.close(); super.onDestroy(); }

    @Override protected void dump(java.io.FileDescriptor fd, java.io.PrintWriter writer, String[] args) {
        super.dump(fd, writer, args);
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0 && layout != null) {
            writer.println("Jianxin layout (geometry only):");
            dumpGeometry(writer, layout, "  ");
        }
    }
    private static void dumpGeometry(java.io.PrintWriter writer, View view, String indent) {
        writer.println(indent + view.getClass().getSimpleName() + " size=" + view.getWidth() + "x" + view.getHeight()
                + " measured=" + view.getMeasuredWidth() + "x" + view.getMeasuredHeight()
                + " top=" + view.getTop() + " bottom=" + view.getBottom() + " paddingBottom=" + view.getPaddingBottom());
        if (view instanceof android.view.ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) dumpGeometry(writer, group.getChildAt(i), indent + "  ");
    }
}
