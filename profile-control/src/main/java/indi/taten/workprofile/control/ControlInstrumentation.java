package indi.taten.workprofile.control;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.Bundle;
import android.os.UserManager;

/** 同一 APK 内的受 ADB 启动入口；普通启动 Activity 不会获得这些权限。 */
public final class ControlInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        UiAutomation automation = null; ControlSession session = null;
        Bundle result = new Bundle(); int code = Activity.RESULT_CANCELED;
        try {
            if (getTargetContext().getSystemService(UserManager.class).isManagedProfile())
                throw new SecurityException("请在主空间激活本工具");
            automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            if (automation == null) throw new SecurityException("需要电脑 ADB 启动的调试会话");
            automation.adoptShellPermissionIdentity("android.permission.INTERACT_ACROSS_USERS_FULL",
                    "android.permission.INTERACT_ACROSS_USERS", "android.permission.CHANGE_OVERLAY_PACKAGES",
                    "android.permission.QUERY_ALL_PACKAGES",
                    // 系统权限页的入口要求此权限；本工具只启动页面，不调用授权/撤权 API。
                    "android.permission.GRANT_RUNTIME_PERMISSIONS");
            session = new ControlSession(getTargetContext());
            ControlSession.publish(session);
            Bundle started = new Bundle(); started.putBoolean("control_active", true); sendStatus(1, started);
            session.awaitStop();
            result.putString("result", "control session stopped"); code = Activity.RESULT_OK;
        } catch (Exception failure) {
            result.putString("error", ControlSession.message(failure));
        } finally {
            if (session != null) ControlSession.clear(session, result.getString("error", ""));
            if (automation != null) automation.dropShellPermissionIdentity();
            finish(code, result);
        }
    }
}
