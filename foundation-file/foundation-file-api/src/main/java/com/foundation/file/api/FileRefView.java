package com.foundation.file.api;

import java.time.LocalDateTime;

/**
 * 文件引用视图（F-09 BAS-17）。
 *
 * @param id       引用 ID（{@code frf_…}）
 * @param fileId   文件 ID
 * @param appCode  归属应用编码
 * @param bizType  业务对象类型
 * @param bizId    业务对象 ID
 * @param createAt 创建时间
 */
public record FileRefView(String id,
                          String fileId,
                          String appCode,
                          String bizType,
                          String bizId,
                          LocalDateTime createAt) {
}
