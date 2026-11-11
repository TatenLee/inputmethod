package indi.tatenlee.inputmethod.keyboard;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.view.MotionEvent;
import android.widget.Button;

/** 使用标准 Button，保留无障碍语义；删除键额外支持按住连续删除。 */
final class KeyButton extends Button {
    private final Handler repeat = new Handler();
    private boolean repeating;
    private final Runnable repeatAction = new Runnable() {
        @Override public void run() {
            if (!isPressed() || !isAttachedToWindow()) return;
            repeating = true;
            performClick();
            repeat.postDelayed(this, 65);
        }
    };
    KeyButton(Context context, String label, KeyboardPalette palette, boolean secondary) {
        super(context);
        setText(label);
        setTextSize(label.length() > 3 ? 14 : 20);
        setMaxLines(1);
        setAutoSizeTextTypeUniformWithConfiguration(11, label.length() > 3 ? 14 : 20, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        setTextColor(palette.text);
        setAllCaps(false);
        setMinWidth(0); setMinimumWidth(0);
        setMinHeight(0); setMinimumHeight(0);
        setPadding(0, 0, 0, 0);
        setStateListAnimator(null);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(secondary ? palette.secondary : palette.key);
        shape.setCornerRadius(7 * getResources().getDisplayMetrics().density);
        setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), shape, null));
        setElevation(1 * getResources().getDisplayMetrics().density);
    }
    void enableRepeat() {
        setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> { repeating = false; repeat.postDelayed(repeatAction, 380); }
                case MotionEvent.ACTION_UP -> {
                    repeat.removeCallbacks(repeatAction);
                    if (repeating) { setPressed(false); return true; }
                }
                case MotionEvent.ACTION_CANCEL -> repeat.removeCallbacks(repeatAction);
            }
            return false;
        });
    }
    @Override protected void onDetachedFromWindow() {
        repeat.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }
}
