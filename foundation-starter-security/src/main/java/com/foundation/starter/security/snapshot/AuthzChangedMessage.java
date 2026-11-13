package com.foundation.starter.security.snapshot;

/**
 * {@code fdn.authz.changed} 事件体（F-07 §4：只带 ID、版本、变化类型，不带敏感字段）。
 *
 * <p>本 starter 不依赖 {@code foundation-authz-api}，以同构记录反序列化事件 JSON。
 *
 * @param eventId         事件 ID
 * @param personId        人员 ID
 * @param appCode         应用编码
 * @param snapshotVersion 新快照版本号
 * @param changeType      变化类型
 */
public record AuthzChangedMessage(String eventId,
                                  String personId,
                                  String appCode,
                                  long snapshotVersion,
                                  String changeType) {
}
