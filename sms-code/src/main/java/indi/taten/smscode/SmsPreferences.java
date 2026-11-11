package indi.taten.smscode;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Telephony;

public final class SmsPreferences {
    public static final int KEEP = 0, DELETE_BUTTON = 1, AUTO_DELETE = 2;
    private final Context context;
    public SmsPreferences(Context context) { this.context = context.getApplicationContext(); }
    public SharedPreferences store() { return context.getSharedPreferences("settings", Context.MODE_PRIVATE); }
    public String smsPackage() {
        String fallback = Telephony.Sms.getDefaultSmsPackage(context);
        return store().getString("sms_package", fallback == null ? "" : fallback);
    }
    public void smsPackage(String value) { SmsRuntime.forgetPendingSms();store().edit().putString("sms_package", value).putInt("deletion_mode", KEEP).apply(); }
    public boolean autoCopy() { return store().getBoolean("auto_copy", true); }
    public void autoCopy(boolean value) { SmsRuntime.forgetPendingSms();store().edit().putBoolean("auto_copy", value).apply(); }
    public int deletionMode() { return store().getInt("deletion_mode", KEEP); }
    public void deletionMode(int value) { SmsRuntime.forgetPendingSms();store().edit().putInt("deletion_mode", value).apply(); }
}
