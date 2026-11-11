package indi.taten.workprofile.control;

import android.app.UiAutomation;
import android.os.UserHandle;
import android.os.UserManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** 专用模拟器工作空间；只读取元数据，不选择任何实际权限。 */
@RunWith(AndroidJUnit4.class)
public final class PermissionPagesDeviceTest {
    @Test public void listsWorkLauncherAppAndRejectsWrongProfileIdentity() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var context = instrumentation.getTargetContext();
        int user = Integer.parseInt(InstrumentationRegistry.getArguments().getString("work_user", "-1"));
        org.junit.Assume.assumeTrue("Requires an explicitly prepared emulator work profile", user > 0);
        UiAutomation automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        automation.adoptShellPermissionIdentity("android.permission.INTERACT_ACROSS_USERS_FULL",
                "android.permission.INTERACT_ACROSS_USERS", "android.permission.QUERY_ALL_PACKAGES");
        ControlSession session = new ControlSession(context);
        ControlSession.publish(session);
        try {
            long serial = context.getSystemService(UserManager.class)
                    .getSerialNumberForUser(UserHandle.getUserHandleForUid(user * 100000));
            var apps = PermissionPages.apps(context, session, user, serial);
            assertTrue("Expected visible third-party work app; check package visibility declarations",
                    apps.stream().anyMatch(app -> app.packageName.equals("indi.taten.smscode") && !app.system));
            assertThrows(SecurityException.class, () -> PermissionPages.apps(context, session, user, serial + 1));
            assertThrows(SecurityException.class, () -> PermissionPages.apps(context, session, 0, 0));
            assertThrows(SecurityException.class, () -> PermissionPages.apps(context, session, 999, 999));
        } finally {
            ControlSession.clear(session, "");
            automation.dropShellPermissionIdentity();
        }
    }
}
