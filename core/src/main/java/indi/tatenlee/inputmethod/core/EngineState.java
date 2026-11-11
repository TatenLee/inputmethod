package indi.tatenlee.inputmethod.core;

import java.util.List;

/** 引擎只提供文本和候选，不依赖键盘控件或 Android 输入框。 */
public final class EngineState {
    public final String preedit;
    public final String commit;
    public final List<String> candidates;
    public final boolean lastPage;
    public final int page;
    public EngineState(String preedit, String commit, List<String> candidates,
                       boolean lastPage, int page) {
        this.preedit = preedit;
        this.commit = commit;
        this.candidates = List.copyOf(candidates);
        this.lastPage = lastPage;
        this.page = page;
    }
    public static EngineState empty() { return new EngineState("", "", List.of(), true, 0); }
}
