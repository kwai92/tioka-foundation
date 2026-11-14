package com.foundation.file.domain;

import java.time.LocalDateTime;

/**
 * 文件对象（{@code fdn_file} 一行的领域视图，F-09 BAS-17 §数据模型）。
 *
 * @param id          文件 ID（{@code fil_…}）
 * @param appCode     归属应用编码（隔离边界）
 * @param bizType     主业务对象类型，可空
 * @param bizId       主业务对象 ID，可空
 * @param fileName    原始文件名
 * @param contentType MIME 类型
 * @param sizeBytes   字节数
 * @param sha256      内容 SHA-256
 * @param storageKind 存储类型
 * @param storageKey  对象键（不含桶）
 * @param dataLabel   数据标签（ADR-040）
 * @param scanStatus  扫描状态
 * @param status      状态
 * @param uploadedBy  上传者
 * @param createAt    创建时间
 * @param updateAt    更新时间
 */
public record StoredFile(String id,
                         String appCode,
                         String bizType,
                         String bizId,
                         String fileName,
                         String contentType,
                         long sizeBytes,
                         String sha256,
                         StorageKind storageKind,
                         String storageKey,
                         DataLabel dataLabel,
                         ScanStatus scanStatus,
                         FileStatus status,
                         String uploadedBy,
                         LocalDateTime createAt,
                         LocalDateTime updateAt) {

    /** 是否归属给定应用（隔离判定）。 */
    public boolean belongsTo(String appCode) {
        return this.appCode != null && this.appCode.equals(appCode);
    }
}
