package com.foundation.common.param.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 参数加密（F-02 BAS-09）。
 *
 * <p>密钥由主密钥经 SHA-256 派生为 32 字节；12 字节随机 IV、128 位认证标签；
 * 信封格式 {@code v1:base64(iv ‖ ciphertext ‖ tag)}，便于后续密钥轮换时按版本号分流。
 */
public class AesGcmParamCipher implements ParamCipher {

    private static final Logger log = LoggerFactory.getLogger(AesGcmParamCipher.class);

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String ENVELOPE_PREFIX = "v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    /** 开发缺省主密钥：仅用于本地；生产必须经环境变量注入（F-00 §5 约定）。 */
    static final String DEV_MASTER_KEY = "foundation-dev-param-master-v1";

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmParamCipher(String masterKey) {
        String effective = masterKey == null || masterKey.isBlank() ? DEV_MASTER_KEY : masterKey;
        if (DEV_MASTER_KEY.equals(effective)) {
            log.warn("foundation.param.master-key 未配置，敏感参数使用开发缺省主密钥，禁止用于生产");
        }
        this.key = new SecretKeySpec(sha256(effective), "AES");
    }

    @Override
    public String encrypt(String plaintext) {
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] envelope = new byte[iv.length + sealed.length];
            System.arraycopy(iv, 0, envelope, 0, iv.length);
            System.arraycopy(sealed, 0, envelope, iv.length, sealed.length);
            return ENVELOPE_PREFIX + Base64.getEncoder().encodeToString(envelope);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("参数加密失败", ex);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        if (ciphertext == null || !ciphertext.startsWith(ENVELOPE_PREFIX)) {
            throw new IllegalArgumentException("密文信封格式不正确");
        }
        byte[] envelope = Base64.getDecoder().decode(ciphertext.substring(ENVELOPE_PREFIX.length()));
        byte[] iv = new byte[IV_LENGTH];
        System.arraycopy(envelope, 0, iv, 0, IV_LENGTH);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(envelope, IV_LENGTH, envelope.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("参数解密失败（主密钥不匹配或密文被篡改）", ex);
        }
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}
