package com.foundation.test.fixture.jdbc;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 规则 9 反例：业务类依赖 JdbcTemplate（绕过 MyBatis-Plus 直连 JDBC）。
 */
public class JdbcTemplateFixture {

    private final JdbcTemplate jdbcTemplate;

    public JdbcTemplateFixture(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Integer count() {
        return jdbcTemplate.queryForObject("SELECT 1", Integer.class);
    }
}
