package com.foundation.org.infrastructure.person;

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
import java.util.HexFormat;

/**
 * 人员敏感字段加解密与查重哈希（F-03 §2.1）。
 *
 * <p>手机与邮箱以 AES-256-GCM 密文落库（{@code *_cipher}），主密钥来自 {@code SecretStoreAdapter}；
 * 另生成 SHA-256 十六进制查重哈希（{@code *_hash}）供唯一性比对。信封与算法口径与
 * {@code AesGcmParamCipher}（BAS-09）保持一致：{@code v1:base64(iv ‖ ciphertext ‖ tag)}。
 */
public class PersonCipher {

    private static final Logger log = LoggerFactory.getLogger(PersonCipher.class);

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String ENVELOPE_PREFIX = "v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    /** 开发缺省主密钥：仅用于本地；生产必须经环境变量注入（F-00 §5 约定）。 */
    public static final String DEV_MASTER_KEY = "foundation-dev-org-person-master-v1";

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public PersonCipher(String masterKey) {
        String effective = masterKey == null || masterKey.isBlank() ? DEV_MASTER_KEY : masterKey;
        if (DEV_MASTER_KEY.equals(effective)) {
            log.warn("foundation.org.person.master-key 未配置，手机 / 邮箱使用开发缺省主密钥，禁止用于生产");
        }
        this.key = new SecretKeySpec(sha256Bytes(effective.getBytes(StandardCharsets.UTF_8)), "AES");
    }

    /** 加密；入参为空返回 {@code null}（不写密文）。 */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
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
            throw new IllegalStateException("人员敏感字段加密失败", ex);
        }
    }

    /** 解密；入参为空返回 {@code null}。 */
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return null;
        }
        if (!ciphertext.startsWith(ENVELOPE_PREFIX)) {
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
            throw new IllegalStateException("人员敏感字段解密失败（主密钥不匹配或密文被篡改）", ex);
        }
    }

    /** 查重哈希（SHA-256 十六进制小写）；入参为空返回 {@code null}。 */
    public String hash(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        return HexFormat.of().formatHex(sha256Bytes(plaintext.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] sha256Bytes(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}
