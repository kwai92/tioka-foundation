package com.foundation.file.application;

import com.foundation.file.api.SignedUrlView;
import com.foundation.file.config.FileProperties;
import com.foundation.file.domain.StoredFile;
import com.foundation.file.storage.ObjectStorageAdapter;
import com.foundation.file.storage.UrlSigner;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * 预签名下载地址签发（F-09 BAS-17）：TTL 取自 {@code foundation.file.signed-url-ttl}，默认 10 分钟。
 *
 * <p>对象存储后端直接给「直读对象存储」的预签名 URL（不把长期凭证发给业务应用）；
 * 本地后端回落到 Foundation 自身的短时地址（{@code /api/fdn/files/signed/{token}}，HMAC 令牌）。
 */
public class SignedUrlService {

    private final ObjectStorageAdapter storage;
    private final UrlSigner signer;
    private final FileProperties properties;

    public SignedUrlService(ObjectStorageAdapter storage, UrlSigner signer, FileProperties properties) {
        this.storage = storage;
        this.signer = signer;
        this.properties = properties;
    }

    /** 为已通过隔离与扫描判定的文件签发下载地址。 */
    public SignedUrlView issue(StoredFile file, String disposition) {
        Duration ttl = properties.getSignedUrlTtl();
        Instant expiresAt = Instant.now().plus(ttl);
        String url = storage.presignedDownload(file.storageKey(), ttl, file.fileName(), file.contentType())
                .orElseGet(() -> fallbackUrl(file, expiresAt));
        return new SignedUrlView(url, "GET", LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
    }

    /** 校验本地后端颁发的下载令牌；无效 / 过期返回空。 */
    public Optional<UrlSigner.SignedRef> verify(String token) {
        return signer.verify(token, Instant.now());
    }

    private String fallbackUrl(StoredFile file, Instant expiresAt) {
        String token = signer.sign(file.appCode(), file.id(), expiresAt);
        return properties.getPublicBaseUrl() + "/api/fdn/files/signed/" + token;
    }
}
