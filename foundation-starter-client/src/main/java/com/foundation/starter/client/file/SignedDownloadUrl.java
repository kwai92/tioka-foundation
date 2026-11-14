package com.foundation.starter.client.file;

/**
 * 预签名下载地址（F-09 BAS-17「GET /api/fdn/files/{id}/signed-url」）。
 *
 * @param url       预签名 URL
 * @param method    HTTP 方法，固定 {@code GET}
 * @param expiresAt 失效时间（ISO-8601 文本）
 */
public record SignedDownloadUrl(String url, String method, String expiresAt) {
}
