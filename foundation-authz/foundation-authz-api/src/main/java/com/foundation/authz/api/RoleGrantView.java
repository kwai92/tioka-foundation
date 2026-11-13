package com.foundation.authz.api;

import java.time.Instant;

/**
 * 授权视图（F-05 BAS-05 §领域模型 {@code RoleGrant}）。
 *
 * @param id                 授权 ID（{@code grt_{ulid}}）
 * @param roleCode           角色编码
 * @param subjectType        {@code PERSON} / {@code ORG_UNIT} / {@code POST}
 * @param subjectId          授予对象 ID
 * @param includeDescendants 是否含下级
 * @param effectiveFrom      生效时间，可空
 * @param effectiveTo        失效时间，可空
 * @param source             {@code MANUAL} / {@code RULE} / {@code SYNC}
 */
public record RoleGrantView(String id,
                            String roleCode,
                            String subjectType,
                            String subjectId,
                            boolean includeDescendants,
                            Instant effectiveFrom,
                            Instant effectiveTo,
                            String source) {
}
