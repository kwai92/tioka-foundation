package com.foundation.sync.api;

import com.foundation.starter.security.permission.RequirePermission;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;
import com.foundation.sync.api.dto.FieldMappingReplaceCmd;
import com.foundation.sync.api.dto.FieldMappingView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * 字段映射 REST（F-08 BAS-11 §10 {@code /api/sync/sources/{id}/field-mappings}）：查询与全量替换。
 *
 * <p>本类不标 {@code @RestController}：由 {@code SyncAutoConfiguration} 以 {@code @Bean} 装配。
 */
@ResponseBody
@RequestMapping("/api/sync/sources")
public class FieldMappingController {

    private final SyncSourceApi sources;

    public FieldMappingController(SyncSourceApi sources) {
        this.sources = sources;
    }

    @GetMapping("/{sourceId}/field-mappings")
    @RequirePermission("fdn:sync:admin")
    public List<FieldMappingView> list(@PathVariable String sourceId) {
        return sources.fieldMappings(sourceId);
    }

    /** 整包替换：提交列表中未出现的映射被删除。 */
    @PutMapping("/{sourceId}/field-mappings")
    @RequirePermission("fdn:sync:admin")
    public List<FieldMappingView> replace(@PathVariable String sourceId,
                                          @RequestBody FieldMappingReplaceCmd cmd) {
        return sources.replaceFieldMappings(sourceId, cmd, operator());
    }

    private static String operator() {
        return PrincipalHolder.current().map(Principal::userId).orElse("system");
    }
}
