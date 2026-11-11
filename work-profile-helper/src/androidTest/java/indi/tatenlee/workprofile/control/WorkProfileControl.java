package indi.tatenlee.workprofile.control;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import java.lang.reflect.InvocationTargetException;

/** 仅随独立测试 APK 构建的 Moto 定点维护；权限在 finally 中撤销，不进入日常辅助工具。 */
public final class WorkProfileControl extends Instrumentation {
    private static final String HIDE_OVERLAY = "com.android.internal.systemui.navbar.hidegestural";
    private static final String AOSP = "com.android.inputmethod.latin";
    private static final String YUYAN = "com.yuyan.pinyin.offline.release/com.yuyan.imemodule.service.ImeService";
    private Bundle arguments;
    @Override public void onCreate(Bundle args) { arguments = args; start(); }

    @Override public void onStart() {
        Bundle output = new Bundle();
        UiAutomation automation = null;
        int code = Activity.RESULT_CANCELED;
        try {
            Context context = getTargetContext();
            String action = arguments.getString("action", "inspect");
            boolean removeMainAosp = "remove-main-aosp".equals(action);
            int userId = Integer.parseInt(arguments.getString(removeMainAosp ? "target_user" : "work_user", "-1"));
            if (Process.myUid() / 100000 != 0 || (removeMainAosp ? userId != 0 : userId <= 0 || userId >= 1000))
                throw new SecurityException("Unexpected user");
            UserHandle user = UserHandle.getUserHandleForUid(userId * 100000);
            if (!context.getSystemService(UserManager.class).getUserProfiles().contains(user)) throw new SecurityException("Not an associated profile");
            if (!java.util.Set.of("inspect", "hide-work-pill", "restore-work-pill", "remove-work-aosp", "remove-main-aosp", "install-work-jianxin").contains(action))
                throw new IllegalArgumentException("Unsupported action");
            automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            java.util.ArrayList<String> permissions = new java.util.ArrayList<>(java.util.List.of(
                    "android.permission.INTERACT_ACROSS_USERS_FULL", "android.permission.INTERACT_ACROSS_USERS", "android.permission.DUMP"));
            if (action.endsWith("work-pill")) permissions.add("android.permission.CHANGE_OVERLAY_PACKAGES");
            if ("remove-work-aosp".equals(action) || removeMainAosp) permissions.add("android.permission.DELETE_PACKAGES");
            if ("install-work-jianxin".equals(action)) {
                permissions.add("android.permission.INSTALL_PACKAGES");
                permissions.add("android.permission.INSTALL_EXISTING_PACKAGES");
            }
            automation.adoptShellPermissionIdentity(permissions.toArray(new String[0]));
            var getSetting = Settings.Secure.class.getMethod("getStringForUser", android.content.ContentResolver.class, String.class, int.class);
            output.putString("default_ime", (String) getSetting.invoke(null, context.getContentResolver(), "default_input_method", userId));
            var imm = context.getSystemService(android.view.inputmethod.InputMethodManager.class);
            var getImes = imm.getClass().getMethod("getEnabledInputMethodListAsUser", UserHandle.class);
            @SuppressWarnings("unchecked")
            var enabled = (java.util.List<android.view.inputmethod.InputMethodInfo>) getImes.invoke(imm, user);
            output.putString("enabled_imes", enabled.stream().map(android.view.inputmethod.InputMethodInfo::getId)
                    .collect(java.util.stream.Collectors.joining(":")));
            output.putString("personal_default_ime", (String) getSetting.invoke(null, context.getContentResolver(), "default_input_method", 0));
            Context work = (Context) Context.class.getMethod("createContextAsUser", UserHandle.class, int.class).invoke(context, user, 0);
            output.putBoolean("work_is_managed", work.getSystemService(UserManager.class).isManagedProfile());
            long serial = context.getSystemService(UserManager.class).getSerialNumberForUser(user);
            output.putLong("work_serial", serial);
            if (!"inspect".equals(action)) {
                if (!"motorola".equalsIgnoreCase(android.os.Build.MANUFACTURER)
                        || !"XT2301-5".equals(android.os.Build.MODEL) || android.os.Build.VERSION.SDK_INT != 36)
                    throw new SecurityException("This maintenance action is pinned to the inspected Moto");
                if (removeMainAosp) {
                    if (userId != 0 || serial != 0 || output.getBoolean("work_is_managed")
                            || !"0".equals(arguments.getString("target_serial")))
                        throw new SecurityException("Main AOSP removal requires explicit personal user 0 / serial 0");
                } else if (userId != 12 || serial != 12 || !output.getBoolean("work_is_managed")
                        || !"12".equals(arguments.getString("work_serial"))) {
                    throw new SecurityException("This action requires the inspected work profile");
                }
            }
            Object service = Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "overlay");
            if (service == null) {
                Bundle received = context.getContentResolver().call(OverlayBinderProvider.URI, "take-overlay", null, null);
                service = received == null ? null : received.getBinder("overlay");
            }
            output.putBoolean("overlay_service_available", service != null);
            if (service != null) {
                Object overlays = Class.forName("android.content.om.IOverlayManager$Stub").getMethod("asInterface", android.os.IBinder.class).invoke(null, service);
                Object info = Class.forName("android.content.om.IOverlayManager").getMethod("getOverlayInfo", String.class, int.class)
                        .invoke(overlays, HIDE_OVERLAY, userId);
                output.putString("overlay", String.valueOf(info));
                if (info != null) output.putBoolean("overlay_enabled", (Boolean) info.getClass().getMethod("isEnabled").invoke(info));
                if (action.endsWith("work-pill")) {
                    boolean desired = "hide-work-pill".equals(action);
                    Object personal = Class.forName("android.content.om.IOverlayManager").getMethod("getOverlayInfo", String.class, int.class)
                            .invoke(overlays, HIDE_OVERLAY, 0);
                    if (info == null || personal == null || !(Boolean) personal.getClass().getMethod("isEnabled").invoke(personal)
                            || output.getBoolean("overlay_enabled") == desired
                            || !"android".equals(info.getClass().getField("targetPackageName").get(info))
                            || !"com.android.internal.navigation_bar_mode_hide".equals(info.getClass().getField("category").get(info))
                            || !(Boolean) info.getClass().getField("isMutable").get(info))
                        throw new IllegalStateException("Overlay state changed since inspection");
                    boolean accepted = (Boolean) Class.forName("android.content.om.IOverlayManager")
                            .getMethod("setEnabled", String.class, boolean.class, int.class).invoke(overlays, HIDE_OVERLAY, desired, userId);
                    output.putBoolean("overlay_write_accepted", accepted);
                    Object after = Class.forName("android.content.om.IOverlayManager").getMethod("getOverlayInfo", String.class, int.class)
                            .invoke(overlays, HIDE_OVERLAY, userId);
                    boolean actual = after != null && (Boolean) after.getClass().getMethod("isEnabled").invoke(after);
                    output.putBoolean("overlay_enabled_after", actual);
                    if (!accepted || actual != desired) throw new IllegalStateException("Overlay write did not take effect");
                }
            }
            if (action.endsWith("work-pill") && service == null) throw new IllegalStateException("Overlay service unavailable");
            if ("remove-work-aosp".equals(action) || removeMainAosp) {
                if (!YUYAN.equals(output.getString("default_ime")) || enabled.stream().noneMatch(i -> YUYAN.equals(i.getId())))
                    throw new IllegalStateException("Yuyan must be selected and enabled before removing AOSP");
                changePackageForUser(context, work, userId, false, output);
            }
            if ("install-work-jianxin".equals(action)) changePackageForUser(context, work, userId, true, output);
            output.putInt("target_user", userId);
            output.putInt("caller_uid", Process.myUid());
            output.putString("result", "inspect".equals(action) ? "read-only inspection passed" : action + " completed");
            code = Activity.RESULT_OK;
        } catch (Throwable error) {
            Throwable actual = error instanceof InvocationTargetException ? ((InvocationTargetException) error).getTargetException() : error;
            output.putString("error", actual.toString());
        } finally {
            if (automation != null) automation.dropShellPermissionIdentity();
            finish(code, output);
        }
    }

    private void changePackageForUser(Context personal, Context target, int userId, boolean installJianxin, Bundle output) throws Exception {
        int actualUser = (Integer) Context.class.getMethod("getUserId").invoke(target);
        if (actualUser != userId || (actualUser != 0 && actualUser != 12) || (installJianxin && actualUser != 12))
            throw new SecurityException("Wrong installer context");
        android.content.pm.PackageInstaller installer = target.getPackageManager().getPackageInstaller();
        var userField = android.content.pm.PackageInstaller.class.getDeclaredField("mUserId");
        userField.setAccessible(true);
        if (userField.getInt(installer) != userId) throw new SecurityException("Wrong installer user");
        String prefix = installJianxin ? "install" : "uninstall";
        String callback = "indi.tatenlee.workprofile.PACKAGE_RESULT_" + Process.myPid();
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        android.content.BroadcastReceiver receiver = new android.content.BroadcastReceiver() {
            @Override public void onReceive(Context c, android.content.Intent intent) {
                output.putInt(prefix + "_status", intent.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS, -999));
                output.putString(prefix + "_message", intent.getStringExtra(android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE));
                done.countDown();
            }
        };
        personal.registerReceiver(receiver, new android.content.IntentFilter(callback), Context.RECEIVER_NOT_EXPORTED);
        android.app.PendingIntent pending = android.app.PendingIntent.getBroadcast(personal, 0,
                new android.content.Intent(callback).setPackage(personal.getPackageName()),
                android.app.PendingIntent.FLAG_CANCEL_CURRENT | android.app.PendingIntent.FLAG_MUTABLE | android.app.PendingIntent.FLAG_ONE_SHOT);
        try {
            if (installJianxin) {
                android.content.pm.PackageInstaller.class.getMethod("installExistingPackage", String.class, int.class, android.content.IntentSender.class)
                        .invoke(installer, "indi.tatenlee.inputmethod", android.content.pm.PackageManager.INSTALL_REASON_USER, pending.getIntentSender());
            } else {
                // DELETE_KEEP_DATA (1) + DELETE_SYSTEM_APP (4), 不含 DELETE_ALL_USERS (2)。
                int flags = android.content.pm.PackageManager.class.getField("DELETE_KEEP_DATA").getInt(null)
                        | android.content.pm.PackageManager.class.getField("DELETE_SYSTEM_APP").getInt(null);
                if (flags != 5) throw new IllegalStateException("Unexpected uninstall flags");
                android.content.pm.PackageInstaller.class.getMethod("uninstall", String.class, int.class, android.content.IntentSender.class)
                        .invoke(installer, AOSP, flags, pending.getIntentSender());
            }
            if (!done.await(25, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Package operation timed out; inspect state before retrying");
            if (output.getInt(prefix + "_status", -999) != android.content.pm.PackageInstaller.STATUS_SUCCESS)
                throw new IllegalStateException("User " + userId + " " + prefix + " failed: " + output.getString(prefix + "_message"));
        } finally {
            pending.cancel();
            personal.unregisterReceiver(receiver);
        }
    }
}
