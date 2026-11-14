package com.foundation.file.api;

/**
 * 上传命令（F-09 BAS-17「POST /api/fdn/files」）。
 *
 * @param appCode     归属应用编码（隔离边界，由调用方解析）
 * @param fileName    原始文件名
 * @param contentType MIME 类型，可空（缺省 {@code application/octet-stream}）
 * @param dataLabel   数据标签（ADR-040），可空（缺省 {@code INTERNAL}）
 * @param bizType     主业务对象类型，可空
 * @param bizId       主业务对象 ID，可空
 */
public record FileUploadCmd(String appCode,
                            String fileName,
                            String contentType,
                            String dataLabel,
                            String bizType,
                            String bizId) {
}
