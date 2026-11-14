package com.foundation.sync.api;

import com.foundation.sync.api.dto.LockFieldsCmd;

import java.util.Set;

/**
 * 本地字段锁定契约（F-08 BAS-11 §5、§10）：锁定 / 解锁人员与组织单元的档案字段。
 *
 * <p>被锁定的字段不被任何来源覆盖，跳过的字段记为一条 {@code LOCKED_FIELD} 冲突（可查、不告警）。
 */
public interface LocalLockApi {

    /** {@code GET /api/persons/{id}/local-locks}：读取人员锁定字段。 */
    Set<String> personLocks(String personId);

    /** {@code PUT /api/persons/{id}/local-locks}：整包替换人员锁定字段。 */
    Set<String> setPersonLocks(String personId, LockFieldsCmd cmd, String operator);

    /** {@code GET /api/org/units/{id}/local-locks}：读取组织单元锁定字段。 */
    Set<String> orgUnitLocks(String orgUnitId);

    /** {@code PUT /api/org/units/{id}/local-locks}：整包替换组织单元锁定字段。 */
    Set<String> setOrgUnitLocks(String orgUnitId, LockFieldsCmd cmd, String operator);
}
