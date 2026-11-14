package com.foundation.sync.domain;

import com.foundation.sync.spi.SyncKey;
import com.foundation.sync.spi.SyncObjectType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 主数据落地端口（F-08 BAS-11 §7、§12）。
 *
 * <p>同步引擎只依赖本端口做幂等合并；真实实现由基础设施层提供（MyBatis-Plus 直连
 * {@code fdn_person} / {@code fdn_org_unit} / {@code fdn_external_identity}），单元测试用内存桩替换。
 * 写方法统一返回落地后的 {@link LandedRecord}（含本地主键与行版本），供发事件与差异比对使用。
 */
public interface MasterDataPort {

    /** 按幂等键查找已落地记录。 */
    Optional<LandedRecord> find(SyncObjectType objectType, SyncKey key);

    /** 某来源下某对象的全部已落地记录（用于全量快照算「已消失对象」的停用差异）。 */
    List<LandedRecord> listBySource(SyncObjectType objectType, String sourceType);

    /**
     * 新增，返回落地后的记录（本地主键 + 行版本）。
     *
     * @param parentKey 上级对象幂等键（组织单元建树用），可空表示根节点
     */
    LandedRecord create(SyncObjectType objectType, SyncKey key, Map<String, Object> fields, SyncKey parentKey);

    /** 更新，返回落地后的记录（行版本 +1）。 */
    LandedRecord update(SyncObjectType objectType, LandedRecord existing, Map<String, Object> fields);

    /** 停用（组织单元「删除」实为停用，见 BAS-02 停用前置校验）。 */
    LandedRecord disable(SyncObjectType objectType, LandedRecord existing);

    /** 移动组织单元；{@code newParentKey} 为空表示移至根。 */
    LandedRecord move(SyncObjectType objectType, LandedRecord existing, SyncKey newParentKey);

    /** 读取本地锁定字段（{@code local_locked_fields}）。 */
    Set<String> lockedFields(SyncObjectType objectType, String localId);

    /** 整包替换本地锁定字段。 */
    void setLockedFields(SyncObjectType objectType, String localId, Set<String> fields);
}
