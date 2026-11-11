package indi.taten.workprofile.control;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.json.JSONObject;

/** 只在 ADB 启动的 instrumentation 存活期间可用，不保存“已授权”的假状态。 */
public final class ControlSession {
    public static final String CHANGED = "indi.taten.workprofile.CONTROL_CHANGED";
    private static volatile ControlSession current;
    public static volatile String lastError = "";
    private final Context context;
    private final CountDownLatch stopped = new CountDownLatch(1);
    private volatile Object overlays;
    private final int ownUser = Process.myUid() / 100000;
    private final Class<?> overlayApi;

    public static final class Profile {
        public final int user; public final long serial; public final boolean hidden; public final String issue; public final boolean hasBackup;
        Profile(int user, long serial, boolean hidden, String issue, boolean hasBackup) {
            this.user = user; this.serial = serial; this.hidden = hidden; this.issue = issue; this.hasBackup = hasBackup;
        }
    }
    public ControlSession(Context context) throws Exception {
        this.context = context.getApplicationContext();
        overlayApi = Class.forName("android.content.om.IOverlayManager");
    }
    public static ControlSession get() { return current; }
    public boolean ready() { return overlays != null; }
    public static void publish(ControlSession session) { current = session; lastError = ""; session.changed(); }
    public static void clear(ControlSession session, String error) {
        if (current == session) current = null;
        lastError = error;
        session.changed();
    }
    public static void connect(IBinder binder) throws Exception {
        ControlSession session = current;
        if (session == null) throw new IllegalStateException("请先用 ADB 激活控制会话");
        if (binder == null || !"android.content.om.IOverlayManager".equals(binder.getInterfaceDescriptor()))
            throw new SecurityException("系统服务类型不匹配");
        session.overlays = Class.forName("android.content.om.IOverlayManager$Stub")
                .getMethod("asInterface", IBinder.class).invoke(null, binder);
        session.changed();
    }
    public void awaitStop() throws InterruptedException { stopped.await(); }
    public void stop() { stopped.countDown(); }
    private void changed() { context.sendBroadcast(new Intent(CHANGED).setPackage(context.getPackageName())); }
    private String backupKey(long serial) { return "original:" + Build.FINGERPRINT + ":" + serial; }
    private android.content.SharedPreferences prefs() { return context.getSharedPreferences("navigation_backups", Context.MODE_PRIVATE); }
    private int id(UserHandle handle) throws Exception { return (Integer) UserHandle.class.getMethod("getIdentifier").invoke(handle); }
    private boolean managed(UserManager manager, int user) throws Exception {
        return (Boolean) UserManager.class.getMethod("isManagedProfile", int.class).invoke(manager, user);
    }
    private Object info(int user) throws Exception {
        if (current != this || overlays == null) throw new IllegalStateException("控制连接已结束，请重新激活");
        return overlayApi.getMethod("getOverlayInfo", String.class, int.class).invoke(overlays, ProfileRules.OVERLAY, user);
    }
    private boolean enabled(Object item) throws Exception { return item != null && (Boolean) item.getClass().getMethod("isEnabled").invoke(item); }
    private void validate(Object item) throws Exception {
        if (item == null) throw new IllegalStateException("系统未提供此隐藏横条配置，暂不支持该机型");
        Class<?> type = item.getClass();
        ProfileRules.validateOverlay((String) type.getField("packageName").get(item),
                (String) type.getField("targetPackageName").get(item), (String) type.getField("category").get(item),
                type.getField("isMutable").getBoolean(item), type.getField("state").getInt(item));
    }
    public synchronized List<Profile> profiles() throws Exception {
        List<Profile> result = new ArrayList<>();
        UserManager manager = context.getSystemService(UserManager.class);
        for (UserHandle handle : manager.getUserProfiles()) {
            int user = id(handle);
            if (user == ownUser || !managed(manager, user)) continue;
            long serial = manager.getSerialNumberForUser(handle);
            try {
                Object item = info(user); validate(item);
                result.add(new Profile(user, serial, enabled(item), "", prefs().contains(backupKey(serial))));
            } catch (Exception failure) {
                result.add(new Profile(user, serial, false, message(failure), false));
            }
        }
        return result;
    }
    /** 每次读取/打开前重新校验归属及序号，拒绝主空间、私密空间和已重建的空间。 */
    public synchronized UserHandle permissionTarget(int user, long expectedSerial) throws Exception {
        if (current != this) throw new IllegalStateException("控制连接已结束，请重新激活");
        UserManager manager = context.getSystemService(UserManager.class);
        UserHandle handle = UserHandle.getUserHandleForUid(user * 100000);
        boolean associated = manager.getUserProfiles().contains(handle);
        ProfileRules.validateProfile(ownUser, user, manager.getSerialNumberForUser(handle), expectedSerial,
                associated, associated && managed(manager, user));
        if (manager.isQuietModeEnabled(handle) || !manager.isUserUnlocked(handle))
            throw new IllegalStateException("请先开启并解锁工作空间");
        return handle;
    }
    public synchronized void change(Profile selected, boolean desired, boolean restore) throws Exception {
        UserManager manager = context.getSystemService(UserManager.class);
        UserHandle handle = UserHandle.getUserHandleForUid(selected.user * 100000);
        boolean associated = manager.getUserProfiles().contains(handle);
        boolean managed = associated && managed(manager, selected.user);
        ProfileRules.validateProfile(ownUser, selected.user, manager.getSerialNumberForUser(handle), selected.serial, associated, managed);
        Object before = info(selected.user); validate(before);
        boolean old = enabled(before);
        if (old != selected.hidden) throw new IllegalStateException("系统设置刚刚发生变化，请刷新后重试");
        String key = backupKey(selected.serial);
        if (restore) {
            String value = prefs().getString(key, null);
            if (value == null) throw new IllegalStateException("本机没有此工作空间的原状态记录");
            JSONObject backup = new JSONObject(value);
            if (backup.getInt("user") != selected.user || backup.getLong("serial") != selected.serial)
                throw new IllegalStateException("原状态记录已失效");
            desired = backup.getBoolean("hidden");
        }
        if (old == desired) return;
        if (!prefs().contains(key)) {
            JSONObject backup = new JSONObject().put("user", selected.user).put("serial", selected.serial).put("hidden", old);
            if (!prefs().edit().putString(key, backup.toString()).commit()) throw new IllegalStateException("原状态保存失败，未修改系统");
        }
        boolean personalBefore = enabled(info(ownUser));
        boolean accepted = (Boolean) overlayApi.getMethod("setEnabled", String.class, boolean.class, int.class)
                .invoke(overlays, ProfileRules.OVERLAY, desired, selected.user);
        if (!accepted || enabled(info(selected.user)) != desired) throw new IllegalStateException("系统未接受修改，请刷新检查");
        if (enabled(info(ownUser)) != personalBefore) throw new IllegalStateException("主空间配置同时发生变化，请检查系统设置");
        changed();
    }
    public static String message(Throwable error) {
        while (error instanceof InvocationTargetException && error.getCause() != null) error = error.getCause();
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
