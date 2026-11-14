package com.foundation.sync.api;

import com.foundation.starter.security.permission.RequirePermission;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;
import com.foundation.sync.api.dto.DiffPreviewView;
import com.foundation.sync.api.dto.SyncRunCmd;
import com.foundation.sync.api.dto.SyncSourceCreateCmd;
import com.foundation.sync.api.dto.SyncSourceUpdateCmd;
import com.foundation.sync.api.dto.SyncSourceView;
import com.foundation.sync.api.dto.SyncTaskView;
import com.foundation.sync.spi.ProbeResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * 同步来源 REST（F-08 BAS-11 §10 {@code /api/sync/sources}）：登记、探测、差异预演与手动触发。
 *
 * <p>只做参数绑定与权限注解，业务在 {@link com.foundation.sync.application.SyncSourceService}。
 *
 * <p>本类不标 {@code @RestController}：由 {@code SyncAutoConfiguration} 以 {@code @Bean} 装配，
 * 保证与 {@link SyncSourceApi} 同生命周期（无持久化上下文时不注册）。
 */
@ResponseBody
@RequestMapping("/api/sync/sources")
public class SyncSourceController {

    private final SyncSourceApi sources;

    public SyncSourceController(SyncSourceApi sources) {
        this.sources = sources;
    }

    @GetMapping
    @RequirePermission("fdn:sync:read")
    public List<SyncSourceView> list() {
        return sources.listSources();
    }

    @PostMapping
    @RequirePermission("fdn:sync:admin")
    public SyncSourceView create(@RequestBody SyncSourceCreateCmd cmd) {
        return sources.createSource(cmd, operator());
    }

    @PutMapping("/{id}")
    @RequirePermission("fdn:sync:admin")
    public SyncSourceView update(@PathVariable String id, @RequestBody SyncSourceUpdateCmd cmd) {
        return sources.updateSource(id, cmd, operator());
    }

    /** 连通性与能力探测（逐项结果）；命名空间下保存前调用。 */
    @PostMapping("/{id}/probe")
    @RequirePermission("fdn:sync:admin")
    public ProbeResult probe(@PathVariable String id) {
        return sources.probe(id);
    }

    /** 差异预演（dry-run）：只写任务与日志，不落地。 */
    @PostMapping("/{id}/preview")
    @RequirePermission("fdn:sync:admin")
    public DiffPreviewView preview(@PathVariable String id, @RequestBody(required = false) SyncRunCmd cmd) {
        return sources.preview(id, cmd, operator());
    }

    /** 手动触发同步：{@code mode} = FULL / INCREMENTAL / RECONCILE；{@code dryRun=true} 时仅预演。 */
    @PostMapping("/{id}/run")
    @RequirePermission("fdn:sync:admin")
    public SyncTaskView run(@PathVariable String id, @RequestBody(required = false) SyncRunCmd cmd) {
        return sources.run(id, cmd, operator());
    }

    private static String operator() {
        return PrincipalHolder.current().map(Principal::userId).orElse("system");
    }
}
