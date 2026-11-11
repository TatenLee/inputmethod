package indi.tatenlee.inputmethod.engine;

/** 仅由 RimeEngine 的单线程队列调用。这个类是 JNI 的稳定边界。 */
final class RimeNative {
    static { System.loadLibrary("jianxin_rime"); }
    static native void initialize(String sharedDirectory, String userDirectory);
    static native String command(int type, int value, boolean nineKey, boolean privateMode);
    static native void shutdown();
}
