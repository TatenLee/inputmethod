package indi.tatenlee.inputmethod.core;

import java.util.ArrayList;
import java.util.List;

/** 保存稳定的 ID，不保存枚举序号，升级时按钮不会错位。 */
public enum ToolbarAction {
    EMOJI("emoji", "表情"), CLIPBOARD("clipboard", "剪贴板"),
    VOICE("voice", "语音"), SETTINGS("settings", "设置");

    public final String id;
    public final String title;
    ToolbarAction(String id, String title) { this.id = id; this.title = title; }

    public static List<ToolbarAction> restore(String saved) {
        List<ToolbarAction> result = new ArrayList<>();
        for (String id : saved.split(",")) {
            for (ToolbarAction action : values()) {
                if (action.id.equals(id) && !result.contains(action)) result.add(action);
            }
        }
        for (ToolbarAction action : values()) if (!result.contains(action)) result.add(action);
        return result;
    }

    public static String save(List<ToolbarAction> actions) {
        List<String> ids = new ArrayList<>();
        for (ToolbarAction action : actions) ids.add(action.id);
        return String.join(",", ids);
    }
}
