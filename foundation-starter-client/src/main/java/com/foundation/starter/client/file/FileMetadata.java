package com.foundation.starter.client.file;

/**
 * 文件元数据（F-09 BAS-17「GET /api/fdn/files/{id}」）。
 *
 * @param id          文件 ID
 * @param appCode     归属应用编码
 * @param fileName    原始文件名
 * @param contentType MIME 类型
 * @param sizeBytes   字节数
 * @param sha256      内容 SHA-256
 * @param storageKind 存储类型（LOCAL / S3）
 * @param dataLabel   数据标签（ADR-040）
 * @param scanStatus  扫描状态
 * @param status      状态（ACTIVE / DELETED）
 * @param bizType     主业务对象类型，可空
 * @param bizId       主业务对象 ID，可空
 * @param uploadedBy  上传者
 * @param createAt    创建时间（ISO-8601 文本）
 */
public record FileMetadata(String id,
                           String appCode,
                           String fileName,
                           String contentType,
                           long sizeBytes,
                           String sha256,
                           String storageKind,
                           String dataLabel,
                           String scanStatus,
                           String status,
                           String bizType,
                           String bizId,
                           String uploadedBy,
                           String createAt) {
}
