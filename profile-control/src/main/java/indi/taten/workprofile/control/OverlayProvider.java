package indi.taten.workprofile.control;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

/** 只允许本机已授权的 ADB shell 交接系统 Binder，不提供远程命令或任意设置入口。 */
public final class OverlayProvider extends ContentProvider {
    public static final String AUTHORITY = "indi.taten.workprofile.overlay";
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (Binder.getCallingUid() != 2000) throw new SecurityException("ADB shell only");
        Bundle result = new Bundle();
        if ("connect".equals(method)) {
            try { ControlSession.connect(extras == null ? null : extras.getBinder("overlay")); }
            catch (Exception error) { throw new IllegalStateException(ControlSession.message(error)); }
        } else if (!"status".equals(method)) throw new IllegalArgumentException("Unknown method");
        ControlSession session = ControlSession.get();
        result.putBoolean("active", session != null);
        result.putBoolean("ready", session != null && session.ready());
        return result;
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
