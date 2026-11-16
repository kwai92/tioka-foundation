package com.foundation.starter.client.local;

import com.foundation.starter.client.org.Person;
import com.foundation.starter.client.org.PersonClient;

import java.util.List;
import java.util.Optional;

/** 人员查询的本地种子实现（ADP-08）：含下级组织时按组织树递归展开。 */
public class LocalPersonClient implements PersonClient {

    private final LocalFoundationData data;

    public LocalPersonClient(LocalFoundationData data) {
        this.data = data;
    }

    @Override
    public Optional<Person> byEmployeeNo(String employeeNo) {
        return data.personByEmployeeNo(employeeNo);
    }

    @Override
    public Optional<Person> byId(String personId) {
        return data.personById(personId);
    }

    @Override
    public List<Person> byOrgUnit(String orgCode, boolean includeSub) {
        return data.personsInOrg(orgCode, includeSub);
    }
}
