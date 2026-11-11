package indi.tatenlee.inputmethod;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.Process;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.view.inputmethod.InputMethodManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.FileDescriptor;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** 使用已安装应用的 UID 检查系统权限隔离，不向任何服务器发送数据。 */
@RunWith(AndroidJUnit4.class)
public final class OfflinePolicyTest {
    @Test public void installedPackageRequestsOnlyMicrophone() throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        var info = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);
        assertEquals(Arrays.asList(Manifest.permission.RECORD_AUDIO), Arrays.asList(info.requestedPermissions));
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.INTERNET));
        assertEquals(context.getApplicationInfo().uid, Process.myUid());
        assertFalse(android.security.NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted());
    }

    @Test public void operatingSystemDeniesIpv4Ipv6TcpAndUdpSockets() throws Exception {
        for (int domain : new int[]{OsConstants.AF_INET, OsConstants.AF_INET6}) {
            for (int type : new int[]{OsConstants.SOCK_STREAM, OsConstants.SOCK_DGRAM}) {
                FileDescriptor descriptor = null;
                try {
                    descriptor = Os.socket(domain, type, 0);
                    fail("Unexpected network socket access: " + domain + "/" + type);
                } catch (ErrnoException denied) {
                    assertTrue("Expected permission denial, got " + denied,
                            denied.errno == OsConstants.EACCES || denied.errno == OsConstants.EPERM);
                    android.util.Log.i("JianxinOfflineTest", "Socket denied: domain=" + domain
                            + " type=" + type + " errno=" + denied.errno + " uid=" + Process.myUid());
                } finally { if (descriptor != null) Os.close(descriptor); }
            }
        }
    }

    @Test public void launcherAndSynthesizedAppListContainNoEntry() {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        var pm = context.getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.getPackageName());
        assertTrue(pm.queryIntentActivities(launcher, 0).isEmpty());
        assertNull(pm.getLaunchIntentForPackage(context.getPackageName()));
        assertTrue(context.getSystemService(LauncherApps.class).getActivityList(context.getPackageName(), Process.myUserHandle()).isEmpty());
    }

    @Test public void imeSettingsEntryRemainsAccessible() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var context = instrumentation.getTargetContext();
        var inputMethods = context.getSystemService(InputMethodManager.class).getInputMethodList();
        var ime = inputMethods.stream().filter(item -> item.getPackageName().equals(context.getPackageName())).findFirst().orElseThrow();
        String settings = "indi.tatenlee.inputmethod.settings.SettingsActivity";
        assertEquals(settings, ime.getSettingsActivity());
        var component = new ComponentName(context.getPackageName(), settings);
        var info = context.getPackageManager().getActivityInfo(component, 0);
        assertTrue(info.enabled && info.exported);
        var activity = instrumentation.startActivitySync(new Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try { assertEquals(settings, activity.getClass().getName()); }
        finally { instrumentation.runOnMainSync(activity::finish); }
    }
}
