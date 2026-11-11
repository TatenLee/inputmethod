package indi.tatenlee.inputmethod.settings;

import android.content.Context;
import android.content.SharedPreferences;
import indi.tatenlee.inputmethod.core.ToolbarAction;
import java.util.List;

/** 设置格式独立于 UI；键名一旦发布便保持稳定。 */
public final class KeyboardSettings {
    private final SharedPreferences prefs;
    public KeyboardSettings(Context context) {
        prefs = context.getSharedPreferences("jianxin_preferences", Context.MODE_PRIVATE);
    }
    public int height(boolean landscape) {
        return clamp(prefs.getInt(landscape ? "height_landscape" : "height_portrait", landscape ? 176 : 248), 140, 360);
    }
    public void height(boolean landscape, int value) {
        prefs.edit().putInt(landscape ? "height_landscape" : "height_portrait", clamp(value, 140, 360)).apply();
    }
    public int extraBottom(boolean landscape) {
        return clamp(prefs.getInt(landscape ? "bottom_landscape" : "bottom_portrait", 8), 0, 64);
    }
    public void extraBottom(boolean landscape, int value) {
        prefs.edit().putInt(landscape ? "bottom_landscape" : "bottom_portrait", clamp(value, 0, 64)).apply();
    }
    public List<ToolbarAction> toolbar() { return ToolbarAction.restore(prefs.getString("toolbar_order", "")); }
    public void toolbar(List<ToolbarAction> actions) { prefs.edit().putString("toolbar_order", ToolbarAction.save(actions)).apply(); }
    public boolean nineKey() { return prefs.getBoolean("nine_key", false); }
    public void nineKey(boolean enabled) { prefs.edit().putBoolean("nine_key", enabled).apply(); }
    public boolean haptic() { return prefs.getBoolean("haptic", true); }
    public void haptic(boolean enabled) { prefs.edit().putBoolean("haptic", enabled).apply(); }
    private static int clamp(int n, int min, int max) { return Math.max(min, Math.min(max, n)); }
}
