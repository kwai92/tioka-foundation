package com.foundation.audit.domain;

/**
 * 审计与登录日志的 ID 前缀（F-09 BAS-10；00-施工总纲 §2.2：{@code {前缀}_{ulid}}）。
 */
public final class AuditIdPrefix {

    /** 审计行。 */
    public static final String AUDIT = "adl";
    /** 登录日志行。 */
    public static final String LOGIN = "lgn";
    /** 审计链封印。 */
    public static final String SEAL = "asl";
    /** 上报批次回执。 */
    public static final String REPORT = "arp";

    private AuditIdPrefix() {
    }
}
