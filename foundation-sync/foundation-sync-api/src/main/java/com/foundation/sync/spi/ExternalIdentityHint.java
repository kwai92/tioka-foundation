package com.foundation.sync.spi;

/**
 * 外部身份候选关联（F-08 BAS-11 §2、ADR-026）。
 *
 * <p>{@code by} 只能是 {@link MatchBy#EMP_NO} 或 {@link MatchBy#VERIFIED_EMAIL}，不得为姓名。
 *
 * @param source     来源标识
 * @param externalId 来源侧标识
 * @param by         匹配键类型
 * @param matchValue 匹配值（工号或已验证邮箱明文）
 */
public record ExternalIdentityHint(String source, String externalId, MatchBy by, String matchValue) {

    public ExternalIdentityHint {
        if (by == null) {
            throw new IllegalArgumentException("matchBy required");
        }
    }
}
