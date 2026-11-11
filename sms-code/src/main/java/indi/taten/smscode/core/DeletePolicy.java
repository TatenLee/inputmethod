package indi.taten.smscode.core;

/** 自动删除必须对应新通知里的同一条短信；重复文本和过期通知均不删除。 */
public final class DeletePolicy {
    public static final String AOSP = "com.android.messaging";
    public static final long MAX_AGE_MS = 10 * 60 * 1000L;
    private DeletePolicy() {}
    public static boolean canAutoDelete(String app, String body, String pendingBody, long now, long notifiedAt,
                                        boolean copied, int visibleMatches, boolean unlocked) {
        return copied && canPrepareAutoDelete(app,body,pendingBody,now,notifiedAt,visibleMatches,unlocked);
    }
    public static boolean canPrepareAutoDelete(String app,String body,String pendingBody,long now,long notifiedAt,int visibleMatches,boolean unlocked) {
        return AOSP.equals(app) && unlocked && visibleMatches == 1 && pendingBody != null
                && now >= notifiedAt && now - notifiedAt <= MAX_AGE_MS
                && OtpExtractor.normalize(body).equals(OtpExtractor.normalize(pendingBody))
                && OtpExtractor.extract(body) != null;
    }
    public static boolean isSingleMessageConfirmation(String title) {
        String normalized = title == null ? "" : title.trim().replace("？", "?").toLowerCase(java.util.Locale.ROOT);
        return java.util.Set.of("delete message?", "delete this message?", "删除短信?", "删除信息?", "删除这条短信?", "删除这条信息?").contains(normalized);
    }
}
