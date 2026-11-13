package com.foundation.org.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foundation.org.api.dto.PersonStatus;
import com.foundation.org.api.dto.PersonType;
import com.foundation.org.domain.Person;
import com.foundation.org.infrastructure.mapper.PersonMapper;
import com.foundation.org.infrastructure.persistence.PersonDO;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 人员仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisPersonRepository implements PersonRepository {

    private final PersonMapper mapper;

    public MybatisPersonRepository(PersonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Person> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisPersonRepository::toDomain);
    }

    @Override
    public Optional<Person> findByEmpNo(String empNo) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<PersonDO>lambdaQuery().eq(PersonDO::getEmpNo, empNo))).map(MybatisPersonRepository::toDomain);
    }

    @Override
    public Optional<Person> findBySourceExternalId(String source, String externalId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<PersonDO>lambdaQuery()
                .eq(PersonDO::getSource, source)
                .eq(PersonDO::getExternalId, externalId))).map(MybatisPersonRepository::toDomain);
    }

    @Override
    public List<Person> findByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return mapper.selectBatchIds(ids).stream().map(MybatisPersonRepository::toDomain).toList();
    }

    @Override
    public boolean existsByEmpNo(String empNo) {
        return mapper.selectCount(Wrappers.<PersonDO>lambdaQuery().eq(PersonDO::getEmpNo, empNo)) > 0;
    }

    @Override
    public boolean existsByMobileHash(String hash, String excludePersonId) {
        return existsByHash(PersonDO::getMobileHash, hash, excludePersonId);
    }

    @Override
    public boolean existsByEmailHash(String hash, String excludePersonId) {
        return existsByHash(PersonDO::getEmailHash, hash, excludePersonId);
    }

    @Override
    public List<Person> search(String q, PersonType type, PersonStatus status,
                               Collection<String> restrictPersonIds, int page, int size) {
        if (restrictPersonIds != null && restrictPersonIds.isEmpty()) {
            return List.of();
        }
        Page<PersonDO> result = mapper.selectPage(new Page<>(page, size),
                searchWrapper(q, type, status, restrictPersonIds));
        return result.getRecords().stream().map(MybatisPersonRepository::toDomain).toList();
    }

    @Override
    public long count(String q, PersonType type, PersonStatus status, Collection<String> restrictPersonIds) {
        if (restrictPersonIds != null && restrictPersonIds.isEmpty()) {
            return 0L;
        }
        return mapper.selectCount(searchWrapper(q, type, status, restrictPersonIds));
    }

    @Override
    public List<Person> findAll() {
        return mapper.selectList(Wrappers.<PersonDO>lambdaQuery().orderByAsc(PersonDO::getEmpNo))
                .stream().map(MybatisPersonRepository::toDomain).toList();
    }

    @Override
    public void insert(Person person) {
        mapper.insert(toDO(person));
    }

    @Override
    public void update(Person person) {
        mapper.update(null, Wrappers.<PersonDO>lambdaUpdate()
                .set(PersonDO::getEmpNo, person.empNo())
                .set(PersonDO::getName, person.name())
                .set(PersonDO::getType, person.type().name())
                .set(PersonDO::getStatus, person.status().name())
                .set(PersonDO::getMobileCipher, person.mobileCipher())
                .set(PersonDO::getEmailCipher, person.emailCipher())
                .set(PersonDO::getMobileHash, person.mobileHash())
                .set(PersonDO::getEmailHash, person.emailHash())
                .set(PersonDO::getSource, person.source())
                .set(PersonDO::getExternalId, person.externalId())
                .set(PersonDO::getVersion, person.version())
                .eq(PersonDO::getId, person.id()));
    }

    private <T> boolean existsByHash(com.baomidou.mybatisplus.core.toolkit.support.SFunction<PersonDO, T> column,
                                     String hash, String excludePersonId) {
        if (hash == null || hash.isBlank()) {
            return false;
        }
        LambdaQueryWrapper<PersonDO> wrapper = Wrappers.<PersonDO>lambdaQuery().eq(column, hash);
        if (excludePersonId != null && !excludePersonId.isBlank()) {
            wrapper.ne(PersonDO::getId, excludePersonId);
        }
        return mapper.selectCount(wrapper) > 0;
    }

    private LambdaQueryWrapper<PersonDO> searchWrapper(String q, PersonType type, PersonStatus status,
                                                       Collection<String> restrictPersonIds) {
        LambdaQueryWrapper<PersonDO> wrapper = Wrappers.<PersonDO>lambdaQuery();
        if (q != null && !q.isBlank()) {
            String pattern = "%" + q.trim() + "%";
            wrapper.and(w -> w.apply("lower(name) LIKE lower({0})", pattern)
                    .or().apply("lower(emp_no) LIKE lower({0})", pattern));
        }
        if (type != null) {
            wrapper.eq(PersonDO::getType, type.name());
        }
        if (status != null) {
            wrapper.eq(PersonDO::getStatus, status.name());
        }
        if (restrictPersonIds != null) {
            wrapper.in(PersonDO::getId, restrictPersonIds);
        }
        wrapper.orderByAsc(PersonDO::getEmpNo);
        return wrapper;
    }

    private static PersonDO toDO(Person person) {
        PersonDO row = new PersonDO();
        row.setId(person.id());
        row.setEmpNo(person.empNo());
        row.setName(person.name());
        row.setType(person.type().name());
        row.setStatus(person.status().name());
        row.setMobileCipher(person.mobileCipher());
        row.setEmailCipher(person.emailCipher());
        row.setMobileHash(person.mobileHash());
        row.setEmailHash(person.emailHash());
        row.setSource(person.source());
        row.setExternalId(person.externalId());
        row.setVersion(person.version());
        return row;
    }

    private static Person toDomain(PersonDO row) {
        return new Person(row.getId(), row.getEmpNo(), row.getName(), PersonType.valueOf(row.getType()),
                PersonStatus.valueOf(row.getStatus()), row.getMobileCipher(), row.getEmailCipher(),
                row.getMobileHash(), row.getEmailHash(), row.getSource(), row.getExternalId(),
                row.getVersion() == null ? 0 : row.getVersion());
    }
}
