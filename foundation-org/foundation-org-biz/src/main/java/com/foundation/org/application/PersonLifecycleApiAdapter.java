package com.foundation.org.application;

import com.foundation.org.api.PersonLifecycleApi;

/**
 * {@link PersonLifecycleApi} 实现（F-04 BAS-14）：只暴露人员状态流转，委托 {@link PersonService}。
 */
public class PersonLifecycleApiAdapter implements PersonLifecycleApi {

    private final PersonService persons;

    public PersonLifecycleApiAdapter(PersonService persons) {
        this.persons = persons;
    }

    @Override
    public void offboard(String personId, String operator) {
        persons.offboard(personId, operator);
    }
}
