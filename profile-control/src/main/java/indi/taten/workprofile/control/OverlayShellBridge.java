package indi.taten.workprofile.control;

import android.content.AttributionSource;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;

/** 由激活脚本通过 app_process 执行；只能交接 overlay 服务，不修改配置。 */
public final class OverlayShellBridge {
    public static void main(String[] args) throws Exception {
        if (Process.myUid() != 2000 || args.length != 1) throw new SecurityException("ADB shell and explicit user required");
        int user = Integer.parseInt(args[0]);
        if (user < 0) throw new IllegalArgumentException("Explicit nonnegative user required");
        android.os.Looper.prepareMainLooper();
        IBinder binder = (IBinder) Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "overlay");
        if (binder == null) throw new IllegalStateException("Overlay service unavailable");
        Object am = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
        Class<?> api = Class.forName("android.app.IActivityManager");
        IBinder token = new Binder();
        Object holder = api.getMethod("getContentProviderExternal", String.class, int.class, IBinder.class, String.class)
                .invoke(am, OverlayProvider.AUTHORITY, user, token, "jianxin-work-profile");
        if (holder == null) throw new IllegalStateException("Control app provider unavailable");
        try {
            Object provider = holder.getClass().getField("provider").get(holder);
            Bundle input = new Bundle(); input.putBinder("overlay", binder);
            Bundle output = (Bundle) Class.forName("android.content.IContentProvider")
                    .getMethod("call", AttributionSource.class, String.class, String.class, String.class, Bundle.class)
                    .invoke(provider, new AttributionSource.Builder(Process.myUid()).setPackageName("com.android.shell").build(),
                            OverlayProvider.AUTHORITY, "connect", null, input);
            if (output == null || !output.getBoolean("ready")) throw new IllegalStateException("Control session not ready");
            System.out.println("PROFILE_CONTROL_READY");
        } finally {
            api.getMethod("removeContentProviderExternalAsUser", String.class, IBinder.class, int.class)
                    .invoke(am, OverlayProvider.AUTHORITY, token, user);
        }
    }
}
