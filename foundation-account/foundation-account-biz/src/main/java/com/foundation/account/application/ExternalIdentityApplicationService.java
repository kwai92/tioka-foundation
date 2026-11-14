package com.foundation.account.application;

import com.foundation.account.api.dto.ExternalIdentityRecordCmd;
import com.foundation.account.api.dto.ExternalIdentitySource;
import com.foundation.account.api.dto.ExternalIdentityView;
import com.foundation.account.api.dto.LinkMode;
import com.foundation.account.domain.AccountIdPrefix;
import com.foundation.account.domain.ExternalIdentity;
import com.foundation.account.domain.IdentityLinkPolicy;
import com.foundation.account.infrastructure.ExternalIdentityRepository;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.id.Ids;
import com.foundation.org.api.PersonApi;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 外部身份应用服务（F-04 §2）：记录「外部身份 → 人员」的关联。
 *
 * <p>自动关联只按工号或**已验证邮箱**匹配且唯一时建立（{@link LinkMode#AUTO}）；
 * **不允许仅凭姓名匹配、不自动建人**，匹配不唯一或无人时留待处理关联。
 */
public class ExternalIdentityApplicationService {

    private final ExternalIdentityRepository identities;
    private final PersonApi persons;

    public ExternalIdentityApplicationService(ExternalIdentityRepository identities, PersonApi persons) {
        this.identities = identities;
        this.persons = persons;
    }

    /** 按来源与外部 ID 取外部身份。 */
    public Optional<ExternalIdentityView> findBySourceAndExternalId(ExternalIdentitySource source, String externalId) {
        if (source == null || externalId == null || externalId.isBlank()) {
            return Optional.empty();
        }
        return identities.findBySourceAndExternalId(source, externalId).map(ExternalIdentity::toView);
    }

    /** 待处理关联列表（{@code person_id IS NULL}）。 */
    public List<ExternalIdentityView> listPending() {
        return identities.findPending().stream().map(ExternalIdentity::toView).toList();
    }

    /** 某人员的外部身份列表。 */
    public List<ExternalIdentityView> listByPerson(String personId) {
        if (personId == null || personId.isBlank()) {
            return List.of();
        }
        return identities.findByPersonId(personId).stream().map(ExternalIdentity::toView).toList();
    }

    /**
     * 记录外部身份（幂等）：已存在则返回既有记录 ID；否则新建并按工号或已验证邮箱尝试自动关联。
     *
     * @return 外部身份 ID（{@code xid_…}）
     */
    @Transactional
    public String record(ExternalIdentityRecordCmd cmd) {
        if (cmd == null || cmd.source() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "外部身份来源必填");
        }
        if (cmd.externalId() == null || cmd.externalId().isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "外部 ID 必填");
        }
        Optional<ExternalIdentity> existing = identities.findBySourceAndExternalId(cmd.source(), cmd.externalId());
        if (existing.isPresent()) {
            return existing.get().id();
        }
        String id = Ids.newId(AccountIdPrefix.EXTERNAL_IDENTITY);
        ExternalIdentity identity = autoCandidate(cmd.empNo(), cmd.email())
                .map(personId -> new ExternalIdentity(id, cmd.source(), cmd.externalId(), personId,
                        LinkMode.AUTO, Instant.now()))
                .orElseGet(() -> new ExternalIdentity(id, cmd.source(), cmd.externalId(), null, null, null));
        identities.insert(identity);
        return id;
    }

    /** 管理员确认关联（{@code link_mode=ADMIN}）；须指定存在的人员。 */
    @Transactional
    public void confirm(String externalIdentityId, String personId, String operator) {
        ExternalIdentity identity = requireIdentity(externalIdentityId);
        if (personId == null || personId.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "确认关联需指定人员 ID");
        }
        if (persons.findById(personId).isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "人员不存在：" + personId);
        }
        identities.update(identity.link(personId, LinkMode.ADMIN, Instant.now()));
    }

    /** 解除关联，外部身份回到待处理状态（F-04 §2 规则 4）。 */
    @Transactional
    public void unlink(String externalIdentityId, String operator) {
        identities.update(requireIdentity(externalIdentityId).unlink());
    }

    /** 按工号或已验证邮箱自动关联：候选唯一时才建立（F-04 §2 规则 1、3；姓名不参与匹配）。 */
    private Optional<String> autoCandidate(String empNo, String email) {
        List<String> candidates = new ArrayList<>();
        if (empNo != null && !empNo.isBlank()) {
            persons.findByEmpNo(empNo).ifPresent(person -> candidates.add(person.id()));
        }
        if (email != null && !email.isBlank()) {
            persons.findByEmail(email).ifPresent(person -> candidates.add(person.id()));
        }
        return IdentityLinkPolicy.uniqueCandidate(candidates);
    }

    private ExternalIdentity requireIdentity(String id) {
        return identities.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "外部身份不存在：" + id));
    }
}
