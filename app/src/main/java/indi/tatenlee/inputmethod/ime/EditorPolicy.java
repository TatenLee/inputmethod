package indi.tatenlee.inputmethod.ime;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;

final class EditorPolicy {
    static boolean privateInput(EditorInfo editor) {
        if (editor == null) return true;
        int type = editor.inputType & InputType.TYPE_MASK_CLASS;
        int variation = editor.inputType & InputType.TYPE_MASK_VARIATION;
        return (editor.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
                || (type == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
                || (type == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));
    }
    static String mode(EditorInfo editor) {
        int type = editor.inputType & InputType.TYPE_MASK_CLASS;
        int variation = editor.inputType & InputType.TYPE_MASK_VARIATION;
        if (type == InputType.TYPE_CLASS_NUMBER || type == InputType.TYPE_CLASS_PHONE || type == InputType.TYPE_CLASS_DATETIME) return "数字";
        if (privateInput(editor) || variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                || variation == InputType.TYPE_TEXT_VARIATION_URI) return "英文";
        return "中文";
    }
    static String enterLabel(EditorInfo editor) {
        if (editor == null || (editor.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return "换行";
        return switch (editor.imeOptions & EditorInfo.IME_MASK_ACTION) {
            case EditorInfo.IME_ACTION_SEND -> "发送";
            case EditorInfo.IME_ACTION_SEARCH -> "搜索";
            case EditorInfo.IME_ACTION_NEXT -> "下一项";
            case EditorInfo.IME_ACTION_GO -> "前往";
            case EditorInfo.IME_ACTION_DONE -> "完成";
            default -> "换行";
        };
    }
}
