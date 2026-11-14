package com.foundation.org.infrastructure;

import com.foundation.org.api.dto.PersonStatus;
import com.foundation.org.api.dto.PersonType;
import com.foundation.org.domain.Person;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 人员仓储（F-03 §7.3，MyBatis-Plus）。 */
public interface PersonRepository {

    Optional<Person> findById(String id);

    Optional<Person> findByEmpNo(String empNo);

    /** 按邮箱查重哈希取人员（外部身份按已验证邮箱自动关联用，F-04 §2）。 */
    Optional<Person> findByEmailHash(String hash);

    Optional<Person> findBySourceExternalId(String source, String externalId);

    List<Person> findByIds(Collection<String> ids);

    boolean existsByEmpNo(String empNo);

    /** 手机查重哈希是否已被他人占用（{@code excludePersonId} 为空表示不排除）。 */
    boolean existsByMobileHash(String hash, String excludePersonId);

    /** 邮箱查重哈希是否已被他人占用。 */
    boolean existsByEmailHash(String hash, String excludePersonId);

    /**
     * 检索。
     *
     * @param restrictPersonIds {@code null} 表示不限；空集合表示无结果（调用方按组织单元解析后传入）
     */
    List<Person> search(String q, PersonType type, PersonStatus status,
                        Collection<String> restrictPersonIds, int page, int size);

    long count(String q, PersonType type, PersonStatus status, Collection<String> restrictPersonIds);

    /** 全量（导出用）。 */
    List<Person> findAll();

    void insert(Person person);

    void update(Person person);
}
