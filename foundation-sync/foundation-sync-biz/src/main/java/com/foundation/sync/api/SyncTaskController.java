package com.foundation.sync.api;

import com.foundation.starter.security.permission.RequirePermission;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;
import com.foundation.sync.api.dto.PageResult;
import com.foundation.sync.api.dto.SyncLogView;
import com.foundation.sync.api.dto.SyncTaskStatus;
import com.foundation.sync.api.dto.SyncTaskView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Instant;
import java.util.List;

/**
 * 同步任务 REST（F-08 BAS-11 §10 {@code /api/sync/tasks}）：任务记录、日志明细、超阈值确认 / 取消与明细下载。
 *
 * <p>本类不标 {@code @RestController}：由 {@code SyncAutoConfiguration} 以 {@code @Bean} 装配。
 */
@ResponseBody
@RequestMapping("/api/sync/tasks")
public class SyncTaskController {

    private final SyncTaskApi tasks;

    public SyncTaskController(SyncTaskApi tasks) {
        this.tasks = tasks;
    }

    @GetMapping
    @RequirePermission("fdn:sync:read")
    public PageResult<SyncTaskView> tasks(@RequestParam(required = false) String sourceId,
                                          @RequestParam(required = false) SyncTaskStatus status,
                                          @RequestParam(required = false) Instant from,
                                          @RequestParam(required = false) Instant to,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return tasks.tasks(sourceId, status, from, to, page, size);
    }

    @GetMapping("/{id}")
    @RequirePermission("fdn:sync:read")
    public SyncTaskView task(@PathVariable String id) {
        return tasks.task(id);
    }

    @GetMapping("/{id}/logs")
    @RequirePermission("fdn:sync:read")
    public List<SyncLogView> logs(@PathVariable String id) {
        return tasks.logs(id);
    }

    /** 下载差异明细（CSV 文本）。 */
    @GetMapping(value = "/{id}/download", produces = "text/csv;charset=UTF-8")
    @RequirePermission("fdn:sync:read")
    public String download(@PathVariable String id) {
        return tasks.download(id);
    }

    /** 超阈值暂停后确认继续。 */
    @PostMapping("/{id}/confirm")
    @RequirePermission("fdn:sync:admin")
    public SyncTaskView confirm(@PathVariable String id) {
        return tasks.confirm(id, operator());
    }

    /** 取消进行中 / 暂停的任务。 */
    @PostMapping("/{id}/cancel")
    @RequirePermission("fdn:sync:admin")
    public SyncTaskView cancel(@PathVariable String id) {
        return tasks.cancel(id, operator());
    }

    private static String operator() {
        return PrincipalHolder.current().map(Principal::userId).orElse("system");
    }
}
