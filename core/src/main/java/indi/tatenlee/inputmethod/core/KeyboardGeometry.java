package indi.tatenlee.inputmethod.core;

/** 所有参数均为 dp。每次测量使用当前窗口大小，不持有屏幕尺寸缓存。 */
public final class KeyboardGeometry {
    private KeyboardGeometry() {}

    public static int bodyHeight(int requested, int windowHeight, boolean landscape,
                                 int navigationBottom, int extraBottom) {
        int available = Math.max(96, windowHeight - navigationBottom - extraBottom - 92);
        int limit = Math.min(available, Math.round(windowHeight * (landscape ? .55f : .48f)));
        return Math.max(96, Math.min(Math.max(140, requested), limit));
    }

    /** Insets 是同一块系统区域的不同描述，取最大值，不相加。 */
    public static int bottomInset(int navigation, int mandatoryGestures, int tappable) {
        return Math.max(0, Math.max(navigation, Math.max(mandatoryGestures, tappable)));
    }
}
