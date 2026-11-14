package com.foundation.authz.application;

import com.foundation.authz.config.SeparationProperties;
import com.foundation.authz.domain.GrantSubjectType;
import com.foundation.authz.domain.RoleGrant;
import com.foundation.authz.domain.RoleMutex;
import com.foundation.authz.infrastructure.RoleMutexRepository;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 三员互斥校验（F-09 BAS-19 §规则）：授予时若「本次角色 ∪ 对象现有角色」命中 {@code fdn_role_mutex}
 * 的任一互斥对则拒绝（{@code CONFLICT}）并写审计 {@code authz.role.mutex_denied}。
 *
 * <p>部署开关 {@code foundation.authz.separation.enabled} 可关闭该约束（小团队）；关闭时放行并写审计
 * {@code authz.role.mutex_switch_off}（开关变更留痕）。
 */
public class RoleMutexGuard {

    private static final Logger LOG = LoggerFactory.getLogger(RoleMutexGuard.class);

    private final RoleMutexRepository mutexes;
    private final RoleMutexChecker checker;
    private final SeparationProperties properties;
    private final AuthzAuditWriter audit;

    public RoleMutexGuard(RoleMutexRepository mutexes, RoleMutexChecker checker,
                          SeparationProperties properties, AuthzAuditWriter audit) {
        this.mutexes = mutexes;
        this.checker = checker;
        this.properties = properties;
        this.audit = audit;
    }

    /**
     * 校验并可能拒绝。
     *
     * @param roleCode    本次授予的角色编码
     * @param subjectType 授予对象类型
     * @param subjectId   授予对象 ID
     * @param existing    授予对象现有授权
     * @param operator    操作人
     */
    public void check(String roleCode, GrantSubjectType subjectType, String subjectId,
                      List<RoleGrant> existing, String operator) {
        List<RoleMutex> pairs = mutexes.all();
        String conflict = existing.stream()
                .map(RoleGrant::roleCode)
                .filter(other -> checker.conflict(roleCode, other, pairs))
                .findFirst()
                .orElse(null);
        if (conflict == null) {
            return;
        }
        String resource = "role:" + roleCode;
        String detail = "role=" + roleCode + ";conflict=" + conflict
                + ";subject=" + subjectType.name() + ":" + subjectId;
        if (!properties.isEnabled()) {
            writeQuietly("authz.role.mutex_switch_off", "SUCCESS", operator, resource, detail);
            return;
        }
        writeQuietly("authz.role.mutex_denied", "DENIED", operator, resource, detail);
        throw new BizException(ErrorCode.CONFLICT, "互斥角色不能同时授予：" + roleCode + " / " + conflict);
    }

    private void writeQuietly(String action, String outcome, String operator, String resource, String detail) {
        try {
            audit.write(action, outcome, operator, resource, detail);
        } catch (RuntimeException ex) {
            LOG.warn("写入鉴权审计失败：{}（动作 {}）", ex.getMessage(), action);
        }
    }
}
