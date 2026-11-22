package com.foundation.authz.application;

import com.foundation.authz.api.OrgAdminScopeView;
import com.foundation.authz.api.RoleGrantCmd;
import com.foundation.authz.config.AuthzProperties;
import com.foundation.authz.domain.GrantSubjectType;
import com.foundation.authz.domain.SubjectMembership;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 组织分级管理员的越权授予拦截（F-05 BAS-27 §分级授权、F-09 BAS-19 §审计）。
 *
 * <p><b>为什么需要本类</b>：{@link OrgAdminScopeService#assertGrantable} 的判定逻辑与其 12 个单测
 * 早已存在，但全仓曾**零生产调用方**——{@code grant()} 只做互斥与高风险复核，REST 层只有类级
 * {@code @RequirePermission("fdn:grant:admin")} 这道粗粒度功能权限。净效果是范围「能配
 * （{@code PUT /org-admin-scopes}）、能查（{@code GET}）」但授权动作不消费它：一个只管华东区、
 * 白名单只有 {@code r.hr} 的分级管理员，实际能把任何角色授给任何组织单元。BAS-27 验收口径
 * 「分级管理员无法越权授予」在生产里不成立。本类把该能力接到授权路径上。
 *
 * <p><b>判定语义只有一处</b>：本类不自实现「范围是否覆盖目标」的判断，一律委托
 * {@link OrgAdminScopeService#assertGrantable}（其内部走 {@code OrgAdminScopeRule.canGrant}）。
 * 本类只负责三件 {@code assertGrantable} 不管的事：豁免判定、目标组织单元解析、开关与审计。
 *
 * <p><b>豁免规则</b>（负责人 2026-11-22 拍板）：**未配置任何范围者不受约束**。有
 * {@code fdn_org_admin_scope} 记录的人才是分级管理员；未配范围者（全局 {@code system_admin} /
 * {@code app_admin}）放行，仍由 {@code @RequirePermission("fdn:grant:admin")} 管。
 * 这不只是宽松取舍——{@code assertGrantable} 在 {@code scopes.list()} 为空时会走到循环外抛
 * {@code FORBIDDEN}，若无条件接入会立刻打挂所有现有授权流程（含全局管理员）。
 * 操作人为空（{@code null}/空白，即内部调用无操作人上下文）同样视为未配范围。
 *
 * <p><b>目标组织单元的解析口径</b>（负责人 2026-11-22 拍板「三种类型全覆盖」）：
 * <ul>
 *   <li>{@code ORG_UNIT}：{@code subjectId} 即组织单元 ID；</li>
 *   <li>{@code PERSON}：**全部**未结束任职的组织单元（主任职与兼职都算，
 *       {@link SubjectMembership#orgUnitsOf}），逐个都须通过才放行（全量 AND）；</li>
 *   <li>{@code POST}：岗位所属组织单元（{@link SubjectMembership#orgUnitsOfPost}）。</li>
 * </ul>
 * {@code PERSON} 取全部而非只取主任职，是为堵住「借兼职影响外区」的绕过口：
 * {@code EffectiveRoleResolver.collectByOrgUnit} 会让被授权人在其**所有**挂靠组织获得角色，
 * 故只校验主任职等于留一条越权通道。已知并接受其代价：跨区兼职人员的授权只能由更高层管理员做。
 *
 * <p><b>解析不出组织单元时拒绝（fail-closed）</b>：目标为空集意味着「无法证明它在范围内」。
 * 注意此处**不得**按「空集的全称量化为真」放行——那是一次 vacuous pass：{@code PERSON} 型授权经
 * {@code collectByPerson} 直接对该人生效（不依赖任何组织单元），{@code POST} 型经
 * {@code collectByPost} 对该岗位下所有人生效，两者都是**真实生效**的授权。
 * 若放行，分级管理员即可通过「给无任职记录的人预授权」或「给组织单元字段为空的岗位授权」越权。
 * 需要这类授权时由未配范围的全局管理员执行（见上「豁免规则」）。
 *
 * <p>部署开关 {@code foundation.authz.org-admin-scope-enforced} 可关闭本约束；关闭时放行并写审计
 * {@code authz.org_admin_scope_switch_off}（开关变更留痕，与 {@link RoleMutexGuard} 同惯例）。
 */
public class OrgAdminScopeGuard {

    private static final Logger LOG = LoggerFactory.getLogger(OrgAdminScopeGuard.class);

    private final OrgAdminScopeService scopeService;
    private final AuthzProperties properties;
    private final SubjectMembership membership;
    private final AuthzAuditWriter audit;

    public OrgAdminScopeGuard(OrgAdminScopeService scopeService, AuthzProperties properties,
                             SubjectMembership membership, AuthzAuditWriter audit) {
        this.scopeService = scopeService;
        this.properties = properties;
        this.membership = membership;
        this.audit = audit;
    }

    /**
     * 校验本次授权是否超出操作人的分级管理范围；越权抛 {@code FORBIDDEN} 并写审计 {@code DENIED}。
     *
     * @param cmd         授权命令（{@code operator()} 即待校验的分级管理员）
     * @param subjectType 已解析的授权对象类型
     */
    public void check(RoleGrantCmd cmd, GrantSubjectType subjectType) {
        String operator = cmd.operator();
        String roleCode = cmd.roleCode();
        String resource = "role:" + roleCode;
        String detail = "role=" + roleCode + ";subject=" + subjectType.name() + ":" + cmd.subjectId()
                + ";operator=" + operator;

        if (!properties.isOrgAdminScopeEnforced()) {
            writeQuietly("authz.org_admin_scope_switch_off", "SUCCESS", operator, resource, detail);
            return;
        }
        if (operator == null || operator.isBlank()) {
            // 无操作人上下文（内部调用）：不存在「分级管理员」身份，不受范围约束
            return;
        }

        List<OrgAdminScopeView> scopes = scopeService.list(operator);
        if (scopes.isEmpty()) {
            return; // 豁免：未配范围者不是分级管理员
        }

        Set<String> targets = targetOrgUnits(subjectType, cmd.subjectId());
        if (targets.isEmpty()) {
            writeQuietly("authz.org_admin_scope_denied", "DENIED", operator, resource, detail + ";reason=no_target_org_unit");
            throw new BizException(ErrorCode.FORBIDDEN,
                    "越权授予：无法确定授权对象 " + subjectType.name() + ":" + cmd.subjectId()
                            + " 所属的组织单元，分级管理员不能授予范围无法判定的对象");
        }

        // 全量 AND：目标的每个组织单元都须落在某条范围内（assertGrantable 自身是「任一范围命中即通过」）
        for (String target : targets) {
            try {
                scopeService.assertGrantable(operator, target, roleCode);
            } catch (BizException ex) {
                writeQuietly("authz.org_admin_scope_denied", "DENIED", operator, resource,
                        detail + ";target=" + target);
                throw ex;
            }
        }
    }

    private Set<String> targetOrgUnits(GrantSubjectType subjectType, String subjectId) {
        if (subjectId == null || subjectId.isBlank()) {
            return Set.of();
        }
        return switch (subjectType) {
            case ORG_UNIT -> Set.of(subjectId);
            case PERSON -> nonBlank(membership.orgUnitsOf(subjectId));
            case POST -> nonBlank(membership.orgUnitsOfPost(subjectId));
        };
    }

    /** 过滤空白 ID：{@code orgUnitsOf} 的实现虽已过滤，但端口契约不保证，判定前再收一次口径。 */
    private static Set<String> nonBlank(Set<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        Set<String> kept = new LinkedHashSet<>();
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                kept.add(id);
            }
        }
        return kept;
    }

    private void writeQuietly(String action, String outcome, String operator, String resource, String detail) {
        try {
            audit.write(action, outcome, operator, resource, detail);
        } catch (RuntimeException ex) {
            LOG.warn("写入鉴权审计失败：{}（动作 {}）", ex.getMessage(), action);
        }
    }
}
