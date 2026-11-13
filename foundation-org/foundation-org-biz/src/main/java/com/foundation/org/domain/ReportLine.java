package com.foundation.org.domain;

/**
 * 汇报关系（F-03 §2.1）：可由同步来源提供，也可按组织负责人推导。
 *
 * <p>主键为 {@code person_id}（复合语义，无独立 ID）。
 *
 * @param personId        人员 ID
 * @param managerPersonId 直属上级人员 ID
 * @param source          {@code local} / {@code derived}
 */
public record ReportLine(String personId, String managerPersonId, String source) {

    /** 由同步或手工维护。 */
    public static final String SOURCE_LOCAL = "local";

    /** 按组织负责人推导。 */
    public static final String SOURCE_DERIVED = "derived";
}
