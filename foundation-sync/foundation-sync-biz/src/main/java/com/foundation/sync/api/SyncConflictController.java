package com.foundation.sync.api;

import com.foundation.starter.security.permission.RequirePermission;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;
import com.foundation.sync.api.dto.PageResult;
import com.foundation.sync.api.dto.SyncConflictResolveCmd;
import com.foundation.sync.api.dto.SyncConflictView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 同步冲突 REST（F-08 BAS-11 §10 {@code /api/sync/conflicts}）：冲突查询与处理。
 *
 * <p>本类不标 {@code @RestController}：由 {@code SyncAutoConfiguration} 以 {@code @Bean} 装配。
 */
@ResponseBody
@RequestMapping("/api/sync/conflicts")
public class SyncConflictController {

    private final SyncConflictApi conflicts;

    public SyncConflictController(SyncConflictApi conflicts) {
        this.conflicts = conflicts;
    }

    @GetMapping
    @RequirePermission("fdn:sync:admin")
    public PageResult<SyncConflictView> list(@RequestParam(required = false) Boolean resolved,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return conflicts.conflicts(resolved, page, size);
    }

    /** 标记冲突已处理。 */
    @PostMapping("/{id}/resolve")
    @RequirePermission("fdn:sync:admin")
    public SyncConflictView resolve(@PathVariable String id,
                                    @RequestBody(required = false) SyncConflictResolveCmd cmd) {
        return conflicts.resolve(id, cmd, operator());
    }

    private static String operator() {
        return PrincipalHolder.current().map(Principal::userId).orElse("system");
    }
}
