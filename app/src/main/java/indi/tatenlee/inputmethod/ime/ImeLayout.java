package indi.tatenlee.inputmethod.ime;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.os.Build;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import indi.tatenlee.inputmethod.core.KeyboardGeometry;
import indi.tatenlee.inputmethod.keyboard.KeyboardView;
import indi.tatenlee.inputmethod.settings.KeyboardSettings;

/** 系统导航区域和用户额外留白分别计算，横竖屏不复用旧尺寸。 */
final class ImeLayout extends FrameLayout {
    private final KeyboardView keyboard;
    private final KeyboardSettings settings;
    private int navigationBottom, left, right;

    ImeLayout(Context context, KeyboardView keyboard, KeyboardSettings settings) {
        super(context);
        this.keyboard = keyboard; this.settings = settings;
        setBackgroundColor(keyboard.backgroundColor());
        setFitsSystemWindows(false);
        addView(keyboard, new LayoutParams(-1, -2));
        setOnApplyWindowInsetsListener((view, insets) -> {
            int oldBottom = navigationBottom, oldLeft = left, oldRight = right;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets nav = insets.getInsets(WindowInsets.Type.navigationBars());
                Insets mandatory = insets.getInsets(WindowInsets.Type.mandatorySystemGestures());
                Insets tappable = insets.getInsets(WindowInsets.Type.tappableElement());
                Insets cutout = insets.getInsets(WindowInsets.Type.displayCutout());
                navigationBottom = KeyboardGeometry.bottomInset(nav.bottom, mandatory.bottom, tappable.bottom);
                left = Math.max(nav.left, cutout.left); right = Math.max(nav.right, cutout.right);
            } else {
                navigationBottom = insets.getSystemWindowInsetBottom();
                left = insets.getSystemWindowInsetLeft(); right = insets.getSystemWindowInsetRight();
            }
            updateSize();
            // Insets 可在父窗口的测量过程中送达；下一帧再请求测量，避免沿用加留白前的高度。
            if (oldBottom != navigationBottom || oldLeft != left || oldRight != right) post(this::requestLayout);
            return insets;
        });
        updateSize();
    }

    int navigationBottom() { return navigationBottom; }

    void updateSize() {
        Configuration c = getResources().getConfiguration();
        boolean landscape = c.orientation == Configuration.ORIENTATION_LANDSCAPE;
        float density = getResources().getDisplayMetrics().density;
        int extra = settings.extraBottom(landscape);
        int height = KeyboardGeometry.bodyHeight(settings.height(landscape), c.screenHeightDp,
                landscape, Math.round(navigationBottom / density), extra);
        keyboard.bodyHeight(Math.round(height * density));
        setPadding(left, 0, right, navigationBottom + Math.round(extra * density));
        requestLayout();
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); requestApplyInsets(); }
    @Override protected void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        navigationBottom = left = right = 0;
        updateSize(); requestApplyInsets();
    }
}
