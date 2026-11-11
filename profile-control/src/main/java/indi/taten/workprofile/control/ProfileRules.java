package indi.taten.workprofile.control;

/** 仅接受关联的 managed profile 和已验证的隐藏横条资源包。 */
public final class ProfileRules {
    public static final String OVERLAY = "com.android.internal.systemui.navbar.hidegestural";
    public static final String CATEGORY = "com.android.internal.navigation_bar_mode_hide";
    private ProfileRules() {}
    public static void validateProfile(int ownUser, int user, long serial, long expectedSerial, boolean associated, boolean managed) {
        if (user < 0 || user == ownUser || serial < 0 || serial != expectedSerial || !associated || !managed)
            throw new SecurityException("工作空间身份发生变化，请刷新后重试");
    }
    public static void validateOverlay(String name, String target, String category, boolean mutable, int state) {
        if (!OVERLAY.equals(name) || !"android".equals(target) || !CATEGORY.equals(category) || !mutable || (state != 2 && state != 3))
            throw new IllegalStateException("此系统没有可用的隐藏横条配置，暂不支持该机型");
    }
}
