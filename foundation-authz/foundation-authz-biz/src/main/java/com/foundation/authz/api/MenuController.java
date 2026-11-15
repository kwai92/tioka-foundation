package com.foundation.authz.api;

import com.foundation.authz.api.MenuApi;
import com.foundation.authz.api.MenuUpdateCmd;
import com.foundation.authz.api.MenuView;
import com.foundation.starter.security.permission.RequirePermission;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * 菜单 REST（F-05 BAS-05 §接口 {@code /api}）：应用菜单树查询与显示属性维护。
 *
 * <p>读 {@code fdn:menu:read}，写 {@code fdn:menu:admin}；清单来源字段（路由/组件/类型/上级）不可改。
 *
 * <p>本类不标 {@code @RestController}：由 {@code AuthzAutoConfiguration} 以 {@code @Bean} 装配，
 * 保证与 {@link MenuApi} 同生命周期（无持久化上下文时不注册）。
 */
@ResponseBody
@RequestMapping("/api")
public class MenuController {

    private final MenuApi menus;

    public MenuController(MenuApi menus) {
        this.menus = menus;
    }

    /** 应用菜单树。 */
    @GetMapping("/apps/{code}/menus")
    @RequirePermission("fdn:menu:read")
    public List<MenuView> menus(@PathVariable String code) {
        return menus.menus(code);
    }

    /** 调整菜单显示属性（名称/图标/排序/显示）。 */
    @PutMapping("/menus/{id}")
    @RequirePermission("fdn:menu:admin")
    public MenuView updateMenu(@PathVariable String id, @RequestBody MenuUpdateRequest request) {
        return menus.updateMenu(new MenuUpdateCmd(id, request.name(), request.icon(), request.sort(),
                request.visible(), operator()));
    }

    private static String operator() {
        return PrincipalHolder.current().map(Principal::userId).orElse("system");
    }

    /** 菜单显示属性调整请求体。 */
    public record MenuUpdateRequest(String name, String icon, Integer sort, Boolean visible) {
    }
}
