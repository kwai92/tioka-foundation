package com.foundation.file.storage;

import com.foundation.file.domain.StorageKind;

/**
 * 存储后端配置（F-09 BAS-17、ADR-026）：由 {@code foundation.file.storage.*} 绑定。
 *
 * @param kind         后端类型
 * @param localRoot    本地后端根目录
 * @param s3Endpoint   S3 兼容端点（MinIO / 客户 OSS），如 {@code http://minio:9000}
 * @param s3Bucket     桶名
 * @param s3Region     区域（SigV4 签名用，MinIO 常用 {@code us-east-1}）
 * @param s3AccessKey  访问键
 * @param s3SecretKey  密钥
 * @param s3PathStyle  是否使用 path-style（MinIO 多为 {@code true}）
 */
public record StorageSettings(StorageKind kind,
                              String localRoot,
                              String s3Endpoint,
                              String s3Bucket,
                              String s3Region,
                              String s3AccessKey,
                              String s3SecretKey,
                              boolean s3PathStyle) {
}
