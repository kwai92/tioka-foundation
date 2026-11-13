package com.foundation.common.param.infrastructure;

/**
 * 敏感参数信封加密（F-02 BAS-09：AES-256-GCM，主密钥经配置注入）。
 *
 * <p>BAS-21 落地 {@code SecretStoreAdapter} 后，主密钥改由密钥存储提供，本接口不变。
 */
public interface ParamCipher {

    /** 加密明文，返回可落库的密文信封。 */
    String encrypt(String plaintext);

    /** 解密信封；密文被篡改时抛异常（GCM 认证失败）。 */
    String decrypt(String ciphertext);
}
