package com.foundation.file.storage;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * 短时下载令牌签发与校验（F-09 BAS-17）：本地存储后端的「预签名」等价物。
 *
 * <p>令牌 = {@code base64url(appCode|fileId|expiresEpochSecond) + "." + base64url(HMAC-SHA256)}；
 * 与 S3 后端的对象存储预签名 URL 用途一致——都不发放长期凭证。
 *
 * <p>密钥来自 {@code foundation.file.signing-secret}；未配置时进程内随机生成
 * （单机可接受，重启后旧链接失效）。
 */
public class UrlSigner {

    private static final String HMAC = "HmacSHA256";

    private final byte[] secret;

    public UrlSigner(String configuredSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            byte[] generated = new byte[32];
            new SecureRandom().nextBytes(generated);
            this.secret = generated;
        } else {
            this.secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
        }
    }

    /** 签发下载令牌。 */
    public String sign(String appCode, String fileId, Instant expiresAt) {
        String payload = appCode + "|" + fileId + "|" + expiresAt.getEpochSecond();
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hmac(encoded));
        return encoded + "." + signature;
    }

    /** 校验令牌：签名不符 / 已过期 / 结构不合法一律视为无效。 */
    public Optional<SignedRef> verify(String token, Instant now) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return Optional.empty();
        }
        String encoded = token.substring(0, dot);
        String provided = token.substring(dot + 1);
        String expected = Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(encoded));
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }
        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        String[] segments = payload.split("\\|");
        if (segments.length != 3) {
            return Optional.empty();
        }
        long expiresEpoch;
        try {
            expiresEpoch = Long.parseLong(segments[2]);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
        Instant expiresAt = Instant.ofEpochSecond(expiresEpoch);
        if (!expiresAt.isAfter(now)) {
            return Optional.empty();
        }
        return Optional.of(new SignedRef(segments[0], segments[1], expiresAt));
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new BizException(ErrorCode.INTERNAL, "HMAC-SHA256 不可用");
        }
    }

    /**
     * 校验通过的令牌内容。
     *
     * @param appCode   归属应用编码
     * @param fileId    文件 ID
     * @param expiresAt 失效时间
     */
    public record SignedRef(String appCode, String fileId, Instant expiresAt) {
    }
}
