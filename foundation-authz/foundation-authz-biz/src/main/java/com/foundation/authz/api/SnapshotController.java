package com.foundation.authz.api;

import com.foundation.authz.api.AuthzSnapshotView;
import com.foundation.authz.api.SnapshotApi;
import com.foundation.authz.api.SnapshotRecomputeCmd;
import com.foundation.authz.api.SnapshotRecomputeResult;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.starter.security.permission.PublicApi;
import com.foundation.starter.security.permission.RequirePermission;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * 授权快照 REST（F-06 BAS-07 §接口 {@code /api/fdn}）：单人/批量取快照、取版本号与手动重算。
 *
 * <p>调用方是应用服务账号（F-06 §301）。应用服务账号没有 {@code person_id}，无法走
 * {@link RequirePermission} 的快照校验，故取快照类端点以「令牌已验签 + 应用编码与路径一致」为准；
 * 这是对 F-06/F-07 服务账号鉴权的最小可用实现。
 *
 * <p>本类不标 {@code @RestController}：由 {@code AuthzAutoConfiguration} 以 {@code @Bean} 装配，
 * 保证与 {@link SnapshotApi} 同生命周期（无持久化上下文时不注册）。
 */
@ResponseBody
@RequestMapping("/api/fdn")
public class SnapshotController {

    private final SnapshotApi snapshots;

    public SnapshotController(SnapshotApi snapshots) {
        this.snapshots = snapshots;
    }

    /**
     * 取单人快照。
     *
     * <p>调用方是应用服务账号（F-06 §301）；应用服务账号没有 {@code person_id}，无法走
     * {@link RequirePermission} 的快照校验，故此处以「令牌已验签 + 应用编码与路径一致」为准。
     */
    @GetMapping("/apps/{appCode}/persons/{personId}/snapshot")
    @PublicApi
    public AuthzSnapshotView find(@PathVariable String appCode, @PathVariable String personId) {
        requireSameApp(appCode);
        return snapshots.find(appCode, personId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "授权快照不存在"));
    }

    /**
     * 取单人快照版本号。
     *
     * <p>同样以「令牌已验签 + 应用编码与路径一致」为准（见 {@link #find}）。
     */
    @GetMapping("/apps/{appCode}/persons/{personId}/snapshot/version")
    @PublicApi
    public VersionResponse version(@PathVariable String appCode, @PathVariable String personId) {
        requireSameApp(appCode);
        long version = snapshots.version(appCode, personId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "授权快照版本不存在"));
        return new VersionResponse(version);
    }

    /**
     * 批量取快照（供启动预热或对账）。
     *
     * <p>同样以「令牌已验签 + 应用编码与路径一致」为准（见 {@link #find}）。
     */
    @PostMapping("/apps/{appCode}/snapshots:batch")
    @PublicApi
    public List<AuthzSnapshotView> batch(@PathVariable String appCode, @RequestBody BatchRequest request) {
        requireSameApp(appCode);
        return snapshots.batch(appCode, request.personIds());
    }

    /** 手动触发重算（幂等）：按人员或按应用。 */
    @PostMapping("/snapshots:recompute")
    @RequirePermission("fdn:snapshot:admin")
    public SnapshotRecomputeResult recompute(@RequestBody SnapshotRecomputeCmd cmd) {
        return snapshots.recompute(cmd);
    }

    /** 校验应用服务账号的应用编码与路径一致；不一致拒绝，避免跨应用取快照。 */
    private static void requireSameApp(String appCode) {
        Principal principal = PrincipalHolder.require();
        if (principal.appCode() != null && !principal.appCode().isBlank()
                && !principal.appCode().equals(appCode)) {
            throw new BizException(ErrorCode.FORBIDDEN, "跨应用取快照被拒绝");
        }
    }

    /** 快照版本号响应体。 */
    public record VersionResponse(long version) {
    }

    /** 批量取快照请求体。 */
    public record BatchRequest(List<String> personIds) {
    }
}
