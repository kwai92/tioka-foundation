package com.foundation.sync.spi;

/**
 * 来源访问凭证引用（F-08 BAS-11 §2、§9）。
 *
 * <p>只存引用不存值（ADR-042 密钥来源）；实际取值由 {@code CredentialCipher}/{@code CredentialApi}
 * 在适配器 {@code authenticate} 时解析。
 *
 * @param sourceType 来源标识
 * @param secretRef  密钥引用（如 {@code secret://foundation/feishu-app-secret}）
 */
public record SyncCredentialsRef(String sourceType, String secretRef) {
}
