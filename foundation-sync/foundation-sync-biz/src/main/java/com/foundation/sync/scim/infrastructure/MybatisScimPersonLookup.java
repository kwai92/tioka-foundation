package com.foundation.sync.scim.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.sync.infrastructure.mapper.SyncPersonMapper;
import com.foundation.sync.infrastructure.persistence.SyncPersonDO;
import com.foundation.sync.scim.domain.ScimPersonLookup;

import java.util.Optional;

/**
 * SCIM 外部身份关联的人员查表实现（F-08 BAS-13 §3）。
 *
 * <p>只按工号（{@code emp_no}）与已验证邮箱哈希（{@code email_hash}）查询 {@code fdn_person}；
 * **不提供按姓名查询**，从实现层面杜绝「仅凭姓名匹配」（ADR-052 第 3 条）。
 */
public class MybatisScimPersonLookup implements ScimPersonLookup {

    private final SyncPersonMapper persons;

    public MybatisScimPersonLookup(SyncPersonMapper persons) {
        this.persons = persons;
    }

    @Override
    public Optional<String> findPersonIdByEmpNo(String empNo) {
        if (empNo == null || empNo.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(persons.selectOne(Wrappers.<SyncPersonDO>lambdaQuery()
                        .eq(SyncPersonDO::getEmpNo, empNo)
                        .last("limit 1")))
                .map(SyncPersonDO::getId);
    }

    @Override
    public Optional<String> findPersonIdByEmailHash(String emailHash) {
        if (emailHash == null || emailHash.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(persons.selectOne(Wrappers.<SyncPersonDO>lambdaQuery()
                        .eq(SyncPersonDO::getEmailHash, emailHash)
                        .last("limit 1")))
                .map(SyncPersonDO::getId);
    }
}
