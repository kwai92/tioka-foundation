package com.foundation.server.assembly;

import com.foundation.authz.domain.SubjectMembership;
import com.foundation.org.domain.Employment;
import com.foundation.org.infrastructure.EmploymentRepository;
import com.foundation.org.infrastructure.OrgUnitRepository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 组织归属端口的真库实现（F-05 BAS-05 §有效角色与互斥、F-06 BAS-07 §受影响人员）。
 *
 * <p><b>为什么放在装配层而不是 org 域</b>：{@link SubjectMembership} 声明在
 * {@code foundation-authz-biz} 的 {@code domain} 包，而施工总纲 §2.1 规定「跨模块只能依赖对方的
 * {@code *-api}」（ArchUnit 规则 {@code modulesDependOnlyOnApi} 守护）。org 域因此**无法**实现该端口，
 * 只能由同时依赖 org-biz 与 authz-biz 的 {@code foundation-server} 在装配层把两者接起来——
 * 这正是「server 只做装配」的定位。
 *
 * <p><b>替代的占位实现</b>：{@code AuthzAutoConfiguration} 以 {@code @ConditionalOnMissingBean} 注册
 * {@code NoopSubjectMembership}（{@code orgUnitsOf}/{@code postsOf} 恒返回空集）。本 Bean 由
 * {@code FoundationServerAssemblyConfiguration} 注册后，占位实现不再装配。占位实现造成的生产缺口是：
 * <ul>
 *   <li>{@code EffectiveRoleResolver} 的**组织单元与岗位两条角色来源恒为空**，有效角色只由「直接授予」构成；</li>
 *   <li>{@code DataScopeResolver} 的 {@code DEPT} / {@code DEPT_AND_SUB} 两档恒得空集合，即该档数据范围失效；</li>
 *   <li>{@code AffectedPersonResolver} 算不出任何受影响人员，授权变更事件的覆盖面为空。</li>
 * </ul>
 *
 * <p><b>口径</b>（与既有实现逐一核对，不自行发明语义）：
 * <ul>
 *   <li>只算**未结束**任职（{@code openByPerson} 过滤 {@code end_date IS NULL}）——已离职/改任的历史任职
 *       不应继续授予权限；{@code deleted} 由 {@code BaseDO} 的 {@code @TableLogic} 自动过滤。</li>
 *   <li>{@code orgUnitsOf} 含**全部**未结束任职的组织单元（主任职与兼职都算）：
 *       {@code EffectiveRoleResolver.collectByOrgUnit} 的语义是「人员挂靠的组织单元命中授权对象即授角色」，
 *       兼职挂靠同样应享有该组织的岗位角色，与 {@code fdn_employment} 允许多条未结束任职的模型一致。</li>
 *   <li>{@code postsOf} 返回 {@code positionId} 并**过滤空值**（{@code position_id} 列可空，任职可不挂岗位）；
 *       对应 {@code GrantSubjectType.POST} 的 {@code subjectId} 即岗位 ID。</li>
 *   <li>{@code descendantsOf} 复用 {@code OrgUnitRepository.descendantIds}，其语义为「自身 + 全部下级」，
 *       与接口 javadoc「组织单元的子树（含自身）」及被替换的占位实现（{@code Set.of(orgUnitId)}）一致；
 *       {@code DataScopeResolver.expandDescendants} 与 {@code EffectiveRoleResolver} 的
 *       {@code includeDescendants} 分支都依赖「含自身」这一口径。</li>
 * </ul>
 */
public class OrgSubjectMembership implements SubjectMembership {

    private final EmploymentRepository employments;
    private final OrgUnitRepository orgUnits;

    public OrgSubjectMembership(EmploymentRepository employments, OrgUnitRepository orgUnits) {
        if (employments == null) {
            throw new IllegalArgumentException("EmploymentRepository is required");
        }
        if (orgUnits == null) {
            throw new IllegalArgumentException("OrgUnitRepository is required");
        }
        this.employments = employments;
        this.orgUnits = orgUnits;
    }

    @Override
    public Set<String> orgUnitsOf(String personId) {
        if (personId == null || personId.isBlank()) {
            return Set.of();
        }
        Set<String> unitIds = new LinkedHashSet<>();
        for (Employment employment : employments.openByPerson(personId)) {
            if (employment.orgUnitId() != null && !employment.orgUnitId().isBlank()) {
                unitIds.add(employment.orgUnitId());
            }
        }
        return unitIds;
    }

    @Override
    public Set<String> postsOf(String personId) {
        if (personId == null || personId.isBlank()) {
            return Set.of();
        }
        Set<String> positionIds = new LinkedHashSet<>();
        for (Employment employment : employments.openByPerson(personId)) {
            // position_id 可空：任职可以不挂岗位，此时不构成任何岗位归属
            if (employment.positionId() != null && !employment.positionId().isBlank()) {
                positionIds.add(employment.positionId());
            }
        }
        return positionIds;
    }

    @Override
    public Set<String> descendantsOf(String orgUnitId) {
        if (orgUnitId == null || orgUnitId.isBlank()) {
            return Set.of();
        }
        return orgUnits.descendantIds(orgUnitId);
    }

    @Override
    public Set<String> personsInOrgUnit(String orgUnitId) {
        if (orgUnitId == null || orgUnitId.isBlank()) {
            return Set.of();
        }
        return employments.openPersonIdsInOrgUnits(List.of(orgUnitId));
    }

    @Override
    public Set<String> personsInPost(String postId) {
        return employments.openPersonIdsInPosition(postId);
    }
}
