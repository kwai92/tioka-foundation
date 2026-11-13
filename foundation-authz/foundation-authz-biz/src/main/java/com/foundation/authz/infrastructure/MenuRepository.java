package com.foundation.authz.infrastructure;

import com.foundation.authz.domain.Menu;

import java.util.List;
import java.util.Optional;

/**
 * 菜单仓储（F-05 BAS-05 §领域模型）。
 *
 * <p>清单来源字段（类型、上级、路由、组件、权限码）与显示属性（名称、图标、排序、显示）
 * 走不同更新入口：前者由清单覆盖，后者只由管理台修改。
 */
public interface MenuRepository {

    /** 应用全部菜单（含 {@code OFFLINE}，供清单差异比对）。 */
    List<Menu> listByApp(String appId);

    /** 按主键查询。 */
    Optional<Menu> findById(String id);

    /** 新增菜单（清单声明）。 */
    void insert(Menu menu);

    /** 用清单覆盖来源字段并复位为在线；不动管理台可改的显示属性。 */
    void updateFromManifest(Menu menu);

    /** 管理台调整显示属性（名称/图标/排序/显示）。 */
    void updateDisplay(Menu menu);

    /** 标下线（清单中已消失）。 */
    void offline(String id);
}
