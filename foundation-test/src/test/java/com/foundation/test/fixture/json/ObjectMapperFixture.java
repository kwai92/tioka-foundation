package com.foundation.test.fixture.json;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 规则 6 反例：方法内 new ObjectMapper()。
 */
public class ObjectMapperFixture {

    public ObjectMapper create() {
        return new ObjectMapper();
    }
}
