package indi.tatenlee.inputmethod.core;

/** 输入框/窗口发生变化后，让此前的异步候选和语音结果失效。主线程持有。 */
public final class SessionGuard {
    private long generation;
    public long current() { return generation; }
    public long advance() { return ++generation; }
    public boolean accepts(long token) { return token == generation; }
}
