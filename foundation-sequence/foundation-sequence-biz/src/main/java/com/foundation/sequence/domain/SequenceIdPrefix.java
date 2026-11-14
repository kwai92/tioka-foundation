package com.foundation.sequence.domain;

/**
 * 编号规则主键前缀（00-施工总纲 §2.2：{@code {前缀}_{ulid}}，列宽 {@code VARCHAR(32)}）。
 */
public final class SequenceIdPrefix {

    /** 编号规则：{@code sqr_{ulid}}。 */
    public static final String SEQUENCE_RULE = "sqr";

    private SequenceIdPrefix() {
    }
}
