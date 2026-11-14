package com.foundation.org.application;

import com.foundation.org.api.PersonApi;
import com.foundation.org.api.dto.PersonView;
import com.foundation.org.domain.Employment;
import com.foundation.org.domain.Person;
import com.foundation.org.infrastructure.EmploymentRepository;
import com.foundation.org.infrastructure.OrgUnitRepository;
import com.foundation.org.infrastructure.PersonRepository;
import com.foundation.org.infrastructure.ReportLineRepository;
import com.foundation.org.infrastructure.person.PersonCipher;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link PersonApi} 只读契约实现（F-03 §5）：供 BAS-05 权限、BAS-06 数据范围等模块依赖。
 *
 * <p>返回明文视图（手机 / 邮箱未脱敏）：本接口是模块间内部边界，脱敏只作用于管理台 HTTP 出参
 * （F-03 §4.2）。
 */
public class PersonApiAdapter implements PersonApi {

    private final PersonRepository persons;
    private final EmploymentRepository employments;
    private final ReportLineRepository reportLines;
    private final OrgUnitRepository orgUnits;
    private final PersonCipher cipher;

    public PersonApiAdapter(PersonRepository persons, EmploymentRepository employments,
                            ReportLineRepository reportLines, OrgUnitRepository orgUnits, PersonCipher cipher) {
        this.persons = persons;
        this.employments = employments;
        this.reportLines = reportLines;
        this.orgUnits = orgUnits;
        this.cipher = cipher;
    }

    @Override
    public Optional<PersonView> findById(String personId) {
        return persons.findById(personId).map(this::plainView);
    }

    @Override
    public Optional<PersonView> findByEmpNo(String empNo) {
        return persons.findByEmpNo(empNo).map(this::plainView);
    }

    @Override
    public Optional<PersonView> findByEmail(String email) {
        String hash = cipher.hash(email);
        if (hash == null) {
            return Optional.empty();
        }
        return persons.findByEmailHash(hash).map(this::plainView);
    }

    @Override
    public Set<String> personsInOrgUnit(String orgUnitId, boolean includeSubUnits) {
        if (orgUnitId == null) {
            return Set.of();
        }
        Set<String> scope = includeSubUnits ? orgUnits.descendantIds(orgUnitId) : Set.of(orgUnitId);
        return scope.isEmpty() ? Set.of() : employments.openPersonIdsInOrgUnits(scope);
    }

    @Override
    public Optional<String> primaryOrgUnitId(String personId) {
        return employments.openPrimary(personId).map(Employment::orgUnitId);
    }

    @Override
    public Optional<String> managerId(String personId) {
        return reportLines.managerOf(personId);
    }

    @Override
    public Set<String> allPersonIds() {
        return persons.findAll().stream().map(Person::id).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private PersonView plainView(Person person) {
        String primaryOrgUnitId = employments.openPrimary(person.id()).map(Employment::orgUnitId).orElse(null);
        String managerPersonId = reportLines.managerOf(person.id()).orElse(null);
        return person.toView(cipher.decrypt(person.mobileCipher()), cipher.decrypt(person.emailCipher()), false,
                primaryOrgUnitId, managerPersonId);
    }
}
