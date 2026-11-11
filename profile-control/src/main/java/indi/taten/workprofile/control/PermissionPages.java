package indi.taten.workprofile.control;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandle;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;

/** 只读取应用列表并打开系统权限 UI；所有授权选择由用户在系统页面完成。 */
public final class PermissionPages {
    private static final String MANAGE = "android.intent.action.MANAGE_APP_PERMISSIONS";
    private PermissionPages() {}
    public static final class App {
        public final String name, packageName;
        public final boolean system;
        App(String name, String packageName, boolean system) {
            this.name = name; this.packageName = packageName; this.system = system;
        }
    }
    public static List<App> apps(Context context, ControlSession session, int user, long serial) throws Exception {
        UserHandle target = session.permissionTarget(user, serial);
        PackageManager pm = context.getPackageManager();
        // 使用桌面公开的工作应用列表，避免 Moto 私有的“已安装应用清单”询问流程。
        var installed = context.getSystemService(LauncherApps.class).getActivityList(null, target);
        List<App> result = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (var launcher : installed) {
            ApplicationInfo info = launcher.getApplicationInfo();
            if (info.uid / 100000 != user || (info.flags & ApplicationInfo.FLAG_INSTALLED) == 0) continue;
            if (!seen.add(info.packageName)) continue;
            CharSequence label = pm.getApplicationLabel(info);
            result.add(new App(label == null ? info.packageName : label.toString(), info.packageName,
                    (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0));
        }
        Collator collator = Collator.getInstance(java.util.Locale.CHINA);
        result.sort((a, b) -> { int order = collator.compare(a.name, b.name);
            return order != 0 ? order : a.packageName.compareTo(b.packageName); });
        return result;
    }
    public static void open(Activity activity, ControlSession session, int user, long serial, String packageName) throws Exception {
        UserHandle target = session.permissionTarget(user, serial);
        PackageManager pm = activity.getPackageManager();
        ApplicationInfo info = (ApplicationInfo) PackageManager.class.getMethod("getApplicationInfoAsUser", String.class, int.class, int.class)
                .invoke(pm, packageName, 0, user);
        if (info.uid / 100000 != user || (info.flags & ApplicationInfo.FLAG_INSTALLED) == 0)
            throw new SecurityException("此应用不在选中的工作空间中");
        String controller = (String) PackageManager.class.getMethod("getPermissionControllerPackageName").invoke(pm);
        if (controller == null) throw new IllegalStateException("系统没有提供权限管理器");
        Intent intent = new Intent(MANAGE).setPackage(controller)
                .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName).putExtra(Intent.EXTRA_USER, target);
        var resolved = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolved == null || !resolved.activityInfo.exported || !controller.equals(resolved.activityInfo.packageName)
                || (resolved.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                || !"android.permission.GRANT_RUNTIME_PERMISSIONS".equals(resolved.activityInfo.permission))
            throw new IllegalStateException("未找到可信的系统权限页面");
        intent.setComponent(new ComponentName(controller, resolved.activityInfo.name));
        if ("motorola".equalsIgnoreCase(Build.MANUFACTURER)) {
            // Moto PRC 的常规入口转到主用户 DeviceGuard；通过系统自身导航图打开原生页面。
            // ID 每次从当前系统资源查找，不跨版本硬编码，也不修改系统包/设备策略。
            var resources = pm.getResourcesForApplication(controller);
            int root = resources.getIdentifier("nav_graph", "id", controller);
            int destination = resources.getIdentifier("app_permission_groups", "id", controller);
            if (root == 0 || destination == 0)
                throw new IllegalStateException("此系统版本没有可用的原生权限入口");
            Bundle args = new Bundle();
            args.putString(Intent.EXTRA_PACKAGE_NAME, packageName);
            args.putParcelable(Intent.EXTRA_USER, target);
            args.putBoolean("_is_system_screen", true);
            args.putLong("com.android.permissioncontroller.extra.SESSION_ID", System.nanoTime());
            intent.setAction("android.intent.action.MANAGE_UNUSED_APPS")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra("android-support-nav:controller:deepLinkIds", new int[]{root, destination})
                    .putExtra("android-support-nav:controller:deepLinkExtras", args);
        }
        activity.startActivity(intent);
    }
}
