package com.foundation.file.api;

import java.util.List;

/**
 * 分片合并命令（F-09 BAS-17「POST /api/fdn/files/multipart:complete」）。
 *
 * @param appCode     归属应用编码
 * @param uploadId    初始化返回的上传会话标识
 * @param fileName    原始文件名
 * @param sizeBytes   合并后的总字节数
 * @param contentType MIME 类型，可空
 * @param sha256      期望的内容 SHA-256（为空时跳过校验）
 * @param dataLabel   数据标签（ADR-040），可空（缺省 {@code INTERNAL}）
 * @param bizType     主业务对象类型，可空
 * @param bizId       主业务对象 ID，可空
 * @param parts       已完成的分片清单
 */
public record MultipartCompleteCmd(String appCode,
                                   String uploadId,
                                   String fileName,
                                   long sizeBytes,
                                   String contentType,
                                   String sha256,
                                   String dataLabel,
                                   String bizType,
                                   String bizId,
                                   List<UploadedPart> parts) {
}
