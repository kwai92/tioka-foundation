package com.foundation.org.api;

import com.foundation.org.api.dto.OrgChangeView;
import com.foundation.org.api.dto.OrgStatus;
import com.foundation.org.api.dto.OrgUnitCreateCmd;
import com.foundation.org.api.dto.OrgUnitTreeNode;
import com.foundation.org.api.dto.OrgUnitType;
import com.foundation.org.api.dto.OrgUnitUpdateCmd;
import com.foundation.org.api.dto.OrgUnitView;
import com.foundation.org.api.dto.PageResult;
import com.foundation.org.application.OrgUnitService;
import com.foundation.starter.security.permission.RequirePermission;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 组织单元 REST（F-03 §5）：只做参数绑定与权限注解，业务在 {@link OrgUnitService}。
 *
 * <p>写接口的 {@code Idempotency-Key} 由 {@code foundation-starter-web}（BAS-24）统一处理。
 */
@RestController
@RequestMapping("/api/fdn/org")
public class OrgUnitController {

    private final OrgUnitService orgUnits;

    public OrgUnitController(OrgUnitService orgUnits) {
        this.orgUnits = orgUnits;
    }

    @GetMapping("/tree")
    @RequirePermission("org:read")
    public List<OrgUnitTreeNode> tree(@RequestParam(required = false) String rootId,
                                      @RequestParam(required = false) Integer depth,
                                      @RequestParam(defaultValue = "false") boolean includeDisabled) {
        return orgUnits.tree(rootId, depth, includeDisabled);
    }

    @GetMapping("/units")
    @RequirePermission("org:read")
    public PageResult<OrgUnitView> list(@RequestParam(required = false) String q,
                                        @RequestParam(required = false) OrgUnitType type,
                                        @RequestParam(required = false) OrgStatus status,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return orgUnits.search(q, type, status, page, size);
    }

    @PostMapping("/units")
    @RequirePermission("org:write")
    @ResponseStatus(HttpStatus.CREATED)
    public OrgUnitView create(@RequestBody OrgUnitCreateCmd cmd) {
        return orgUnits.create(cmd);
    }

    @PutMapping("/units/{id}")
    @RequirePermission("org:write")
    public OrgUnitView update(@PathVariable String id, @RequestBody OrgUnitUpdateCmd cmd) {
        return orgUnits.update(id, cmd);
    }

    @DeleteMapping("/units/{id}")
    @RequirePermission("org:write")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@PathVariable String id) {
        orgUnits.disable(id);
    }

    @GetMapping("/units/{id}/changes")
    @RequirePermission("org:read")
    public List<OrgChangeView> changes(@PathVariable String id,
                                       @RequestParam(required = false) Integer limit) {
        return orgUnits.changes(id, limit);
    }
}
