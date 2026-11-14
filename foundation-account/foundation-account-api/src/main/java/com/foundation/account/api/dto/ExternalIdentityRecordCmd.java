package com.foundation.account.api.dto;

/**
 * 记录外部身份入参（F-04 §2 关联规则）：同步或首次登录时携带该来源的身份声明。
 *
 * <p>自动关联只按工号或**已验证邮箱**匹配且唯一时建立；匹配不唯一或无对应人员时不自动建人，只留待处理关联。
 * **不允许仅凭姓名匹配**。
 *
 * @param source     来源
 * @param externalId 外部 ID
 * @param empNo      外部身份携带的工号，可空
 * @param email      外部身份携带的已验证邮箱，可空
 * @param operator   操作人（审计用）
 */
public record ExternalIdentityRecordCmd(ExternalIdentitySource source,
                                        String externalId,
                                        String empNo,
                                        String email,
                                        String operator) {
}
