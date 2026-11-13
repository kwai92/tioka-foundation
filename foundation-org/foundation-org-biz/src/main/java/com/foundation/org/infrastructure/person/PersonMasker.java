package com.foundation.org.infrastructure.person;

/**
 * 人员敏感字段脱敏（F-03 §4.2、§7.1）。
 *
 * <p>列表 / 详情出参先解密再脱敏：手机保留后 4 位（{@code tail-4}），邮箱保留首个字符与
 * {@code @} 后域名（{@code domain}）。模式取自 {@code foundation.org.person.mask.*}。
 */
public class PersonMasker {

    /** 手机脱敏：保留后 4 位。 */
    public static final String MOBILE_TAIL_4 = "tail-4";

    /** 邮箱脱敏：保留首个字符与 {@code @} 后域名。 */
    public static final String EMAIL_DOMAIN = "domain";

    private final String mobileMode;
    private final String emailMode;

    public PersonMasker() {
        this(MOBILE_TAIL_4, EMAIL_DOMAIN);
    }

    public PersonMasker(String mobileMode, String emailMode) {
        this.mobileMode = mobileMode == null || mobileMode.isBlank() ? MOBILE_TAIL_4 : mobileMode;
        this.emailMode = emailMode == null || emailMode.isBlank() ? EMAIL_DOMAIN : emailMode;
    }

    public String mobileMode() {
        return mobileMode;
    }

    public String emailMode() {
        return emailMode;
    }

    /** 手机脱敏：保留后 4 位，其余以 {@code *} 替代；入参为空原样返回。 */
    public String maskMobile(String mobile) {
        if (mobile == null || mobile.isBlank() || mobile.length() <= 4) {
            return mobile;
        }
        int keep = 4;
        return "*".repeat(mobile.length() - keep) + mobile.substring(mobile.length() - keep);
    }

    /** 邮箱脱敏：保留首字符与 {@code @} 后域名；入参为空或非法邮箱原样返回。 */
    public String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return email;
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        return local.charAt(0) + "*".repeat(Math.max(local.length() - 1, 1)) + domain;
    }
}
