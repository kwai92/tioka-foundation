package com.foundation.sync.scim.domain;

import java.util.Optional;

/**
 * SCIM 外部身份关联判定（F-08 BAS-13 §3、F-04 §2、ADR-026 / ADR-052 第 3 条）。
 *
 * <p>匹配顺序：工号 → 已验证邮箱哈希；**绝不按姓名匹配**。命中则生成 {@code AUTO} 关联；
 * 未命中返回 {@link Optional#empty()}，由调用方落「待处理关联」，**不自动建人**。
 */
public class ScimExternalIdentityResolver {

    /** 匹配依据：工号。 */
    public static final String MATCH_EMP_NO = "EMP_NO";

    /** 匹配依据：已验证邮箱。 */
    public static final String MATCH_VERIFIED_EMAIL = "VERIFIED_EMAIL";

    /**
     * 一次关联判定结果。
     *
     * @param personId 命中的人员 ID
     * @param matchBy  匹配依据（{@link #MATCH_EMP_NO} / {@link #MATCH_VERIFIED_EMAIL}）
     * @param evidence 证据值（工号或邮箱哈希，供审计留痕）
     */
    public record Resolution(String personId, String matchBy, String evidence) {
    }

    /**
     * 判定 SCIM User 是否可关联到已存在人员。
     *
     * @param user   SCIM 用户
     * @param lookup 人员查表端口（仅工号与邮箱哈希）
     */
    public Optional<Resolution> resolve(ScimUser user, ScimPersonLookup lookup) {
        if (user == null || lookup == null) {
            return Optional.empty();
        }
        String empNo = user.effectiveEmpNo();
        if (empNo != null) {
            Optional<String> byEmpNo = lookup.findPersonIdByEmpNo(empNo);
            if (byEmpNo.isPresent()) {
                return byEmpNo.map(personId -> new Resolution(personId, MATCH_EMP_NO, empNo));
            }
        }
        String emailHash = ScimHash.sha256Hex(user.email());
        if (emailHash != null) {
            Optional<String> byEmail = lookup.findPersonIdByEmailHash(emailHash);
            if (byEmail.isPresent()) {
                return byEmail.map(personId -> new Resolution(personId, MATCH_VERIFIED_EMAIL, emailHash));
            }
        }
        return Optional.empty();
    }
}
