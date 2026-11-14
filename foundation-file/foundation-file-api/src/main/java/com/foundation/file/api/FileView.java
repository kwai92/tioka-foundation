package com.foundation.file.api;

import java.time.LocalDateTime;

/**
 * 文件元数据视图（F-09 BAS-17「GET /api/fdn/files/{id}」）。
 *
 * <p>不含 {@code storage_key} 等实现细节；存储位置由服务端掌握。
 *
 * @param id          文件 ID
 * @param appCode     归属应用编码
 * @param fileName    原始文件名
 * @param contentType MIME 类型
 * @param sizeBytes   字节数
 * @param sha256      内容 SHA-256
 * @param storageKind 存储类型（LOCAL / S3）
 * @param dataLabel   数据标签（ADR-040）
 * @param scanStatus  扫描状态（PENDING / CLEAN / INFECTED / SKIPPED）
 * @param status      状态（ACTIVE / DELETED）
 * @param bizType     主业务对象类型，可空
 * @param bizId       主业务对象 ID，可空
 * @param uploadedBy  上传者
 * @param createAt    创建时间
 */
public record FileView(String id,
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
                       LocalDateTime createAt) {
}
