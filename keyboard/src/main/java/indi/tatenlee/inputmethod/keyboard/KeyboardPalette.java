package indi.tatenlee.inputmethod.keyboard;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;

public final class KeyboardPalette {
    public final int background, key, text, muted, accent, secondary;
    public KeyboardPalette(Resources resources) {
        boolean dark = (resources.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        background = Color.parseColor(dark ? "#202725" : "#EAF0ED");
        key = Color.parseColor(dark ? "#34403B" : "#FFFFFF");
        text = Color.parseColor(dark ? "#F0F5F2" : "#20362E");
        muted = Color.parseColor(dark ? "#ACC0B7" : "#63796E");
        accent = Color.parseColor(dark ? "#92D8BD" : "#176B58");
        secondary = Color.parseColor(dark ? "#29352F" : "#D9E5DE");
    }
}
