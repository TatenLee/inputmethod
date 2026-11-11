package indi.taten.smscode.core;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 自有离线规则：必须有验证码语境；多个候选时拒绝猜测。 */
public final class OtpExtractor {
    private static final Pattern KEYWORD = Pattern.compile("验证码|校验码|驗證碼|驗證代碼|动态码|動態碼|确认码|認證碼|verification\\s+code|security\\s+code|one[ -]?time\\s+(?:code|password)|\\botp\\b|your\\s+(?:login\\s+)?code", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN = Pattern.compile("(?<![A-Za-z0-9])[A-Za-z0-9]{4,8}(?![A-Za-z0-9])");
    private static final Pattern NON_CODE_CONTEXT = Pattern.compile("订单|訂單|客服电话|客服電話|手机号|手机号码|電話|电话|金额|金額|订单号|訂單號|余额|餘額|order|phone|tel\\.?|amount|balance", Pattern.CASE_INSENSITIVE);
    private OtpExtractor() {}
    public static String extract(String body) {
        if (body == null || body.length() > 4096) return null;
        String text = normalize(body);
        Matcher words = KEYWORD.matcher(text);
        ArrayList<int[]> contexts = new ArrayList<>();
        while (words.find()) contexts.add(new int[]{words.start(), words.end()});
        if (contexts.isEmpty()) return null;
        Set<String> matches = new LinkedHashSet<>();
        Matcher tokens = TOKEN.matcher(text);
        while (tokens.find()) {
            String token = tokens.group();
            if (!token.chars().anyMatch(Character::isDigit)) continue;
            int distance = Integer.MAX_VALUE;
            int keywordEnd = -1;
            for (int[] context : contexts) {
                int gap = tokens.start() >= context[1] ? tokens.start() - context[1]
                        : context[0] >= tokens.end() ? context[0] - tokens.end() : 10000;
                if (gap < distance) { distance = gap; keywordEnd = context[1]; }
            }
            if (distance > 32) continue;
            String preceding = text.substring(Math.max(0, tokens.start()-18), tokens.start());
            Matcher forbidden = NON_CODE_CONTEXT.matcher(preceding);
            boolean unrelated = false;
            while (forbidden.find()) {
                int absolute = Math.max(0, tokens.start()-18) + forbidden.start();
                if (keywordEnd <= tokens.start() && absolute >= keywordEnd) unrelated = true;
            }
            if (unrelated || preceding.contains("://") || preceding.endsWith("/")) continue;
            String after = text.substring(tokens.end(), Math.min(text.length(), tokens.end()+3)).toLowerCase(Locale.ROOT);
            if (after.startsWith("元") || after.startsWith("年") || after.startsWith("分钟") || after.startsWith("秒")) continue;
            matches.add(token);
            if (matches.size() > 1) return null;
        }
        return matches.isEmpty() ? null : matches.iterator().next();
    }
    public static String normalize(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC).replace('\u00a0',' ').trim();
    }
}
