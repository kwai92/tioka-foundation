package com.foundation.authz.api;

import com.foundation.authz.api.AuthzSnapshotView;
import com.foundation.authz.api.SnapshotApi;
import com.foundation.authz.config.AuthzProperties;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 会话快照 REST（F-10 §2.4/§9 的「{@code /me} 类接口」）。
 *
 * <p>前端基座启动的唯一入口：返回当前用户 + 可见菜单树 + 权限码集合（即授权快照）；路径沿用
 * M0-02 的 {@code GET /api/system/auth/me}。
 *
 * <p>本类不标 {@code @RestController}：由 {@code AuthzAutoConfiguration} 以 {@code @Bean} 装配，
 * 保证与 {@link SnapshotApi} 同生命周期（无持久化上下文时不注册）。
 */
@ResponseBody
@RequestMapping("/api/system/auth")
public class MeController {

    private final SnapshotApi snapshots;
    private final AuthzProperties properties;

    public MeController(SnapshotApi snapshots, AuthzProperties properties) {
        this.snapshots = snapshots;
        this.properties = properties;
    }

    /** 当前登录用户在本应用的授权快照（登录即可）。 */
    @GetMapping("/me")
    public AuthzSnapshotView me(@RequestParam(required = false) String app) {
        Principal principal = PrincipalHolder.require();
        String personId = principal.personId();
        if (personId == null || personId.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "当前令牌缺少 person_id，无法取会话快照");
        }
        String appCode = app == null || app.isBlank() ? properties.getPlatformAppCode() : app;
        return snapshots.find(appCode, personId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "授权快照不存在"));
    }
}
