package indi.tatenlee.workprofile.control;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;

/** 测试 APK 内的一次性 Binder 交接；仅 ADB shell 可存，仅本项目工具可取。 */
public final class OverlayBinderProvider extends ContentProvider {
    public static final Uri URI = Uri.parse("content://indi.tatenlee.workprofile.test.overlay");
    private IBinder overlay;
    @Override public boolean onCreate() { return true; }
    @Override public synchronized Bundle call(String method, String arg, Bundle extras) {
        int uid = Binder.getCallingUid();
        Bundle result = new Bundle();
        if ("supply-overlay".equals(method) && uid == 2000) {
            overlay = extras.getBinder("overlay");
            if (overlay == null) throw new IllegalArgumentException("Missing overlay binder");
            result.putBoolean("accepted", true);
            return result;
        }
        try {
            if ("take-overlay".equals(method) && uid == getContext().getPackageManager()
                    .getPackageUid("indi.tatenlee.workprofile", 0)) {
                result.putBinder("overlay", overlay);
                overlay = null;
                return result;
            }
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            throw new IllegalStateException(e);
        }
        throw new SecurityException("Unsupported caller or method");
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
