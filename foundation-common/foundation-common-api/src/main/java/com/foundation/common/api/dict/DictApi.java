package com.foundation.common.api.dict;

import java.util.List;

/**
 * 字典契约（F-02 BAS-09）：供 starter 与前端查询、供管理端写入。
 *
 * <p>写操作按「作用域 + code」天然幂等（部分唯一索引），重复提交不产生重复行。
 */
public interface DictApi {

    /** 查某字典类型的启用项，按 {@code sort_no} 升序；应用项优先于全局项（同名类型）。 */
    List<DictItemView> items(String appCode, String typeCode);

    /** 新建或按（作用域 + code）更新字典类型。 */
    void upsertType(DictTypeCmd cmd);

    /** 新建或按（类型 + code）更新字典项。 */
    void upsertItem(DictItemCmd cmd);
}
