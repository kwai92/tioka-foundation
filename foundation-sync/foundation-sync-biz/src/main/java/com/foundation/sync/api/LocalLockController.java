package com.foundation.sync.api;

import com.foundation.starter.security.permission.RequirePermission;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;
import com.foundation.sync.api.dto.LockFieldsCmd;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Set;

/**
 * 本地字段锁定 REST（F-08 BAS-11 §5、§10）：锁定 / 解锁人员与组织单元的档案字段。
 *
 * <p>被锁定的字段不被任何来源覆盖，跳过时记为 {@code LOCKED_FIELD} 冲突。整包替换语义：
 * 提交集合即目标锁定集合（空集合即全部解锁）。权限沿用组织域档案写入码。
 *
 * <p>本类不标 {@code @RestController}：由 {@code SyncAutoConfiguration} 以 {@code @Bean} 装配。
 */
@ResponseBody
@RequestMapping("/api")
public class LocalLockController {

    private final LocalLockApi locks;

    public LocalLockController(LocalLockApi locks) {
        this.locks = locks;
    }

    @GetMapping("/persons/{id}/local-locks")
    @RequirePermission("org:person:read")
    public Set<String> personLocks(@PathVariable String id) {
        return locks.personLocks(id);
    }

    @PutMapping("/persons/{id}/local-locks")
    @RequirePermission("org:person:write")
    public Set<String> setPersonLocks(@PathVariable String id, @RequestBody(required = false) LockFieldsCmd cmd) {
        return locks.setPersonLocks(id, cmd, operator());
    }

    @GetMapping("/org/units/{id}/local-locks")
    @RequirePermission("org:read")
    public Set<String> orgUnitLocks(@PathVariable String id) {
        return locks.orgUnitLocks(id);
    }

    @PutMapping("/org/units/{id}/local-locks")
    @RequirePermission("org:write")
    public Set<String> setOrgUnitLocks(@PathVariable String id,
                                       @RequestBody(required = false) LockFieldsCmd cmd) {
        return locks.setOrgUnitLocks(id, cmd, operator());
    }

    private static String operator() {
        return PrincipalHolder.current().map(Principal::userId).orElse("system");
    }
}
