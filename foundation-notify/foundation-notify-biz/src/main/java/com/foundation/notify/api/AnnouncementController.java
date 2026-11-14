package com.foundation.notify.api;

import com.foundation.notify.api.AnnouncementApi;
import com.foundation.notify.api.AnnouncementCmd;
import com.foundation.notify.api.AnnouncementPageView;
import com.foundation.notify.api.AnnouncementScopeType;
import com.foundation.notify.api.AnnouncementView;
import com.foundation.starter.security.permission.RequirePermission;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Instant;

/**
 * 公告 REST（F-09 BAS-16 §接口 {@code /api/fdn/announcements}）：列表、草稿、发布与下线。
 *
 * <p>本类不标 {@code @RestController}：由 {@code NotifyAutoConfiguration} 以 {@code @Bean} 装配。
 */
@ResponseBody
@RequestMapping("/api/fdn/announcements")
public class AnnouncementController {

    private static final String PERMISSION = "fdn:announcement:publish";

    private final AnnouncementApi announcements;

    public AnnouncementController(AnnouncementApi announcements) {
        this.announcements = announcements;
    }

    /** 公告列表（要求登录）。 */
    @GetMapping
    public AnnouncementPageView list(@RequestParam(required = false) String status,
                                     @RequestParam(required = false) String scope,
                                     @RequestParam(required = false) String cursor,
                                     @RequestParam(defaultValue = "20") int size) {
        PrincipalHolder.require();
        return announcements.list(status, scope, cursor, size);
    }

    /** 新建公告草稿。 */
    @PostMapping
    @RequirePermission(PERMISSION)
    public AnnouncementView create(@RequestBody AnnouncementRequest request) {
        String publisherId = PrincipalHolder.current().map(Principal::userId).orElse("system");
        return announcements.create(toCmd(request), publisherId);
    }

    /** 更新公告草稿。 */
    @PutMapping("/{id}")
    @RequirePermission(PERMISSION)
    public AnnouncementView update(@PathVariable String id, @RequestBody AnnouncementRequest request) {
        return announcements.update(id, toCmd(request));
    }

    /** 发布公告。 */
    @PostMapping("/{id}/publish")
    @RequirePermission(PERMISSION)
    public AnnouncementView publish(@PathVariable String id) {
        return announcements.publish(id);
    }

    /** 下线公告。 */
    @PostMapping("/{id}/offline")
    @RequirePermission(PERMISSION)
    public AnnouncementView offline(@PathVariable String id) {
        return announcements.offline(id);
    }

    private static AnnouncementCmd toCmd(AnnouncementRequest request) {
        return new AnnouncementCmd(request.title(), request.content(),
                AnnouncementScopeType.of(request.scopeType()), request.scopeId(), request.dataLabel(),
                request.publishAt(), request.expireAt());
    }

    /** 公告草稿 / 更新请求体。 */
    public record AnnouncementRequest(String title,
                                      String content,
                                      String scopeType,
                                      String scopeId,
                                      String dataLabel,
                                      Instant publishAt,
                                      Instant expireAt) {
    }
}
