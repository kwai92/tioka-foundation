package com.foundation.file.api;

import java.util.List;

/**
 * 文件引用契约（F-09 BAS-17）：业务对象与文件的「引用 / 解除引用 / 列举」关系。
 *
 * <p>引用是删除保护与归档的判据：有引用时只允许逻辑删除（对齐 ADR-029 第 13 条）。
 */
public interface FileRefApi {

    /** 建立业务对象对文件的引用（同一四元组幂等）。 */
    FileRefView addRef(String fileId, FileRefCmd cmd);

    /** 解除引用（不存在时幂等返回）。 */
    void removeRef(String fileId, FileRefCmd cmd);

    /** 列举某个文件的全部引用（按应用隔离）。 */
    List<FileRefView> refs(String appCode, String fileId);
}
