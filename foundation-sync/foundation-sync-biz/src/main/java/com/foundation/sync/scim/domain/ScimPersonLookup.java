package com.foundation.sync.scim.domain;

import java.util.Optional;

/**
 * SCIM 外部身份关联时的人员查表端口（F-08 BAS-13 §3、F-04 §2）。
 *
 * <p>只暴露两条**允许的**匹配通道：工号（{@code emp_no}）与已验证邮箱哈希（{@code email_hash}）。
 * 不提供按姓名查询，从端口层面杜绝「仅凭姓名匹配」（ADR-052 第 3 条 / ADR-026）。
 */
public interface ScimPersonLookup {

    /** 按工号查已存在人员 ID。 */
    Optional<String> findPersonIdByEmpNo(String empNo);

    /** 按邮箱哈希（SHA-256 十六进制小写）查已存在人员 ID。 */
    Optional<String> findPersonIdByEmailHash(String emailHash);
}
