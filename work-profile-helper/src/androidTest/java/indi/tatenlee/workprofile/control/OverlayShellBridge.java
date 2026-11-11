package indi.tatenlee.workprofile.control;

import android.content.AttributionSource;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;

/** 由电脑通过 app_process 执行；只交接 overlay 服务引用，不执行系统修改。 */
public final class OverlayShellBridge {
    public static void main(String[] args) throws Exception {
        if (Process.myUid() != 2000 || args.length != 0) throw new SecurityException("ADB shell only");
        android.os.Looper.prepareMainLooper();
        IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "overlay");
        if (binder == null) throw new IllegalStateException("Overlay service unavailable to shell");
        Bundle bundle = new Bundle();
        bundle.putBinder("overlay", binder);
        // 与 AOSP `content` 命令相同：以真实 shell UID/package 获取外部 provider。
        Object am = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
        Class<?> amInterface = Class.forName("android.app.IActivityManager");
        IBinder token = new Binder();
        String authority = OverlayBinderProvider.URI.getAuthority();
        Object holder = amInterface.getMethod("getContentProviderExternal", String.class, int.class, IBinder.class, String.class)
                .invoke(am, authority, 0, token, "jianxin-overlay-check");
        if (holder == null) throw new IllegalStateException("Test provider unavailable");
        try {
            Object provider = holder.getClass().getField("provider").get(holder);
            AttributionSource source = new AttributionSource.Builder(Process.myUid()).setPackageName("com.android.shell").build();
            Bundle result = (Bundle) Class.forName("android.content.IContentProvider")
                    .getMethod("call", AttributionSource.class, String.class, String.class, String.class, Bundle.class)
                    .invoke(provider, source, authority, "supply-overlay", null, bundle);
            System.out.println("Overlay reference delivered: " + (result != null && result.getBoolean("accepted")));
        } finally {
            amInterface.getMethod("removeContentProviderExternalAsUser", String.class, IBinder.class, int.class)
                    .invoke(am, authority, token, 0);
        }
    }
}
