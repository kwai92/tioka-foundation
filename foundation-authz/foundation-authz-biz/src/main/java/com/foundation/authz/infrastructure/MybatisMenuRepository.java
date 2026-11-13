package com.foundation.authz.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.authz.domain.LifecycleStatus;
import com.foundation.authz.domain.Menu;
import com.foundation.authz.domain.MenuType;
import com.foundation.authz.infrastructure.mapper.MenuMapper;
import com.foundation.authz.infrastructure.persistence.MenuDO;

import java.util.List;
import java.util.Optional;

/** 菜单仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisMenuRepository implements MenuRepository {

    private final MenuMapper mapper;

    public MybatisMenuRepository(MenuMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Menu> listByApp(String appId) {
        return mapper.selectList(Wrappers.<MenuDO>lambdaQuery()
                        .eq(MenuDO::getAppId, appId)
                        .orderByAsc(MenuDO::getCode))
                .stream().map(MybatisMenuRepository::toDomain).toList();
    }

    @Override
    public Optional<Menu> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisMenuRepository::toDomain);
    }

    @Override
    public void insert(Menu menu) {
        MenuDO row = new MenuDO();
        row.setId(menu.id());
        row.setAppId(menu.appId());
        row.setCode(menu.code());
        row.setName(menu.name());
        row.setType(menu.type());
        row.setParentId(menu.parentId());
        row.setRoutePath(menu.routePath());
        row.setComponent(menu.component());
        row.setIcon(menu.icon());
        row.setSort(menu.sort());
        row.setVisible(menu.visible());
        row.setPermissionCode(menu.permissionCode());
        row.setStatus(menu.status());
        mapper.insert(row);
    }

    @Override
    public void updateFromManifest(Menu menu) {
        mapper.update(null, Wrappers.<MenuDO>lambdaUpdate()
                .set(MenuDO::getType, menu.type())
                .set(MenuDO::getParentId, menu.parentId())
                .set(MenuDO::getRoutePath, menu.routePath())
                .set(MenuDO::getComponent, menu.component())
                .set(MenuDO::getPermissionCode, menu.permissionCode())
                .set(MenuDO::getStatus, LifecycleStatus.ACTIVE)
                .eq(MenuDO::getId, menu.id()));
    }

    @Override
    public void updateDisplay(Menu menu) {
        mapper.update(null, Wrappers.<MenuDO>lambdaUpdate()
                .set(MenuDO::getName, menu.name())
                .set(MenuDO::getIcon, menu.icon())
                .set(MenuDO::getSort, menu.sort())
                .set(MenuDO::getVisible, menu.visible())
                .eq(MenuDO::getId, menu.id()));
    }

    @Override
    public void offline(String id) {
        mapper.update(null, Wrappers.<MenuDO>lambdaUpdate()
                .set(MenuDO::getStatus, LifecycleStatus.OFFLINE)
                .eq(MenuDO::getId, id));
    }

    private static Menu toDomain(MenuDO row) {
        return new Menu(row.getId(), row.getAppId(), row.getCode(), row.getName(),
                row.getType() == null ? MenuType.MENU.name() : row.getType(),
                row.getParentId(), row.getRoutePath(), row.getComponent(), row.getIcon(),
                row.getSort() == null ? 0 : row.getSort(),
                row.getVisible() == null || row.getVisible(),
                row.getPermissionCode(), row.getStatus());
    }
}
