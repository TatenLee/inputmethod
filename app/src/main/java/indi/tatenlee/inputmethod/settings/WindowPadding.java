package indi.tatenlee.inputmethod.settings;

import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

/** 设置页在 Android 15 的全屏绘制区域内避开状态栏、导航栏与弹出的键盘。 */
final class WindowPadding {
    static void setContent(android.app.Activity activity, View content) {
        android.widget.FrameLayout frame = new android.widget.FrameLayout(activity);
        frame.setBackgroundColor(0xfff5f7f4);
        frame.addView(content, new android.widget.FrameLayout.LayoutParams(-1, -1));
        activity.setContentView(frame);
        apply(frame);
    }
    private static void apply(View root) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                var space = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(space.left, space.top, space.right, space.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            view.post(() -> {
                View focused = view.findFocus();
                if (focused instanceof android.widget.EditText) {
                    android.graphics.Rect rectangle = new android.graphics.Rect();
                    focused.getDrawingRect(rectangle);
                    focused.requestRectangleOnScreen(rectangle, true);
                }
            });
            return insets;
        });
        root.requestApplyInsets();
    }
}
