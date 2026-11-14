package com.foundation.file.api;

/**
 * 文件引用命令（F-09 BAS-17「POST/DELETE /api/fdn/files/{id}/refs」）。
 *
 * @param appCode 归属应用编码（隔离边界）
 * @param bizType 业务对象类型
 * @param bizId   业务对象 ID
 */
public record FileRefCmd(String appCode, String bizType, String bizId) {
}
