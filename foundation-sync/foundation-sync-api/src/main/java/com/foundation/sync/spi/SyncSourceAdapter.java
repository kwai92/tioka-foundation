package com.foundation.sync.spi;

import java.util.Optional;

/**
 * 同步来源适配器（F-08 BAS-11 §2，ADR-078 第 1 条）。
 *
 * <p>BAS-11 定义、BAS-12 与 F-14 实现；适配器以插件包提供，**只依赖本 SPI 与 {@code -api} 契约，
 * 不依赖中台 {@code -biz}**（ADR-078 第 8 条）。本接口签名与能力矩阵字段即 F-14 的契约，
 * 改名或改签名须走 ADR-030 变更流程。
 *
 * <p>能力缺失时引擎降级并在同步报告里标注「能力缺失」，不静默跳过。
 */
public interface SyncSourceAdapter {

    /** 来源标识，与 {@code fdn_sync_source.source_type} 一致（FEISHU / LDAP / SCIM / WECOM / DINGTALK …）。 */
    String sourceType();

    /** 能力声明：支持哪些对象、哪种增量方式、是否提供外部身份。引擎据此编排（ADR-078 第 2 条）。 */
    SourceCapabilities capabilities();

    /** 认证：API 凭证 / 应用授权 / OAuth 客户端凭证；凭证复用 ADR-042 的密钥来源，只存引用。 */
    void authenticate(SyncCredentialsRef credentials);

    /** 连通性与能力探测：来源配置保存前调用，返回逐项结果（不抛异常，失败项列出）。 */
    ProbeResult probe();

    /** 全量快照：游标由引擎透传，适配器内部实现分页、限流与退避重试。 */
    Page<ExternalRecord> fetchFull(SyncObjectType objectType, PageCursor cursor);

    /** 增量：按来源声明的增量方式拉取；无增量能力时返回空页。 */
    Page<ExternalRecord> fetchIncremental(SyncObjectType objectType, IncrementalCursor cursor);

    /** 单对象回查：事件回调或对账修复时按外部 ID 取一条。 */
    Optional<ExternalRecord> fetchOne(SyncObjectType objectType, String externalId);

    /** 增量游标推进点：引擎在增量成功后持久化到 {@code fdn_sync_task.cursor_json}。 */
    IncrementalCursor advanceCursor(IncrementalCursor cursor, Page<ExternalRecord> page);

    /** 外部身份提供：按工号 / 已验证邮箱给出候选关联（沿用 ADR-052 第 3 条，不按姓名匹配）。 */
    Optional<ExternalIdentityHint> resolveExternalIdentity(ExternalRecord record);
}
