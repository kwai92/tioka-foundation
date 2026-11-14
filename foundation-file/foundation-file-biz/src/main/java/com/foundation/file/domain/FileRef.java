package com.foundation.file.domain;

import java.time.LocalDateTime;

/**
 * 业务对象对文件的引用（{@code fdn_file_ref} 一行的领域视图，F-09 BAS-17 §数据模型）。
 *
 * @param id       引用 ID（{@code frf_…}）
 * @param fileId   文件 ID
 * @param appCode  归属应用编码
 * @param bizType  业务对象类型
 * @param bizId    业务对象 ID
 * @param createAt 创建时间
 */
public record FileRef(String id,
                      String fileId,
                      String appCode,
                      String bizType,
                      String bizId,
                      LocalDateTime createAt) {
}
