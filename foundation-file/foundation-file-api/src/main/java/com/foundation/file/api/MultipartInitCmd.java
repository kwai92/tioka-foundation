package com.foundation.file.api;

/**
 * 分片上传初始化命令（F-09 BAS-17「POST /api/fdn/files/multipart:init」）。
 *
 * @param appCode     归属应用编码
 * @param fileName    原始文件名
 * @param sizeBytes   期望的总字节数（用于切分片数）
 * @param contentType MIME 类型，可空
 * @param dataLabel   数据标签（ADR-040），可空（缺省 {@code INTERNAL}）
 * @param bizType     主业务对象类型，可空
 * @param bizId       主业务对象 ID，可空
 */
public record MultipartInitCmd(String appCode,
                               String fileName,
                               long sizeBytes,
                               String contentType,
                               String dataLabel,
                               String bizType,
                               String bizId) {
}
