package com.foundation.file.api;

/**
 * 已完成的分片（F-09 BAS-17「multipart:complete」入参）。
 *
 * @param partNumber 分片序号（从 1 起）
 * @param etag       分片校验标识（S3 为响应 {@code ETag}；LOCAL 可空）
 */
public record UploadedPart(int partNumber, String etag) {
}
