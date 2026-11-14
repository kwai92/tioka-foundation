package com.foundation.file.api;

import java.time.LocalDateTime;

/**
 * 预签名下载地址（F-09 BAS-17「GET /api/fdn/files/{id}/signed-url」）。
 *
 * @param url       预签名 URL（LOCAL 后端为 Foundation 自身的短时地址；S3 后端为对象存储地址）
 * @param method    HTTP 方法，固定 {@code GET}
 * @param expiresAt 失效时间
 */
public record SignedUrlView(String url, String method, LocalDateTime expiresAt) {
}
