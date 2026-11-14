package com.foundation.sync.application;

import com.foundation.sync.api.LocalLockApi;
import com.foundation.sync.api.dto.LockFieldsCmd;
import com.foundation.sync.domain.MasterDataPort;
import com.foundation.sync.spi.SyncObjectType;

import java.util.Set;

/**
 * 本地字段锁定应用服务（F-08 BAS-11 §5、§10）。
 *
 * <p>被锁定的字段不被任何来源覆盖；落地时跳过并记为 {@code LOCKED_FIELD} 冲突。锁定位持久化在
 * {@code fdn_person.local_locked_fields} / {@code fdn_org_unit.local_locked_fields}，读写在
 * {@link MasterDataPort}（基础设施层）。
 */
public class LocalLockService implements LocalLockApi {

    private final MasterDataPort masterData;

    public LocalLockService(MasterDataPort masterData) {
        this.masterData = masterData;
    }

    @Override
    public Set<String> personLocks(String personId) {
        return masterData.lockedFields(SyncObjectType.PERSON, personId);
    }

    @Override
    public Set<String> setPersonLocks(String personId, LockFieldsCmd cmd, String operator) {
        masterData.setLockedFields(SyncObjectType.PERSON, personId, fieldsOf(cmd));
        return masterData.lockedFields(SyncObjectType.PERSON, personId);
    }

    @Override
    public Set<String> orgUnitLocks(String orgUnitId) {
        return masterData.lockedFields(SyncObjectType.ORG_UNIT, orgUnitId);
    }

    @Override
    public Set<String> setOrgUnitLocks(String orgUnitId, LockFieldsCmd cmd, String operator) {
        masterData.setLockedFields(SyncObjectType.ORG_UNIT, orgUnitId, fieldsOf(cmd));
        return masterData.lockedFields(SyncObjectType.ORG_UNIT, orgUnitId);
    }

    private static Set<String> fieldsOf(LockFieldsCmd cmd) {
        return cmd == null ? Set.of() : cmd.fields();
    }
}
