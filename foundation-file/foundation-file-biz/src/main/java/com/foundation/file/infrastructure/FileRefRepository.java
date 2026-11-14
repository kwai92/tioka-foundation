package com.foundation.file.infrastructure;

import com.foundation.file.domain.FileRef;

import java.util.List;
import java.util.Optional;

/** 文件引用仓储（F-09 BAS-17）：引用是删除保护与归档的判据。 */
public interface FileRefRepository {

    /** 按唯一四元组查引用。 */
    Optional<FileRef> find(String fileId, String appCode, String bizType, String bizId);

    /** 追加一行引用（唯一约束由库保证）。 */
    void insert(FileRef ref);

    /** 解除引用（不存在时幂等）。 */
    void delete(String fileId, String appCode, String bizType, String bizId);

    /** 列举某文件在某应用下的全部引用。 */
    List<FileRef> listByFile(String appCode, String fileId);

    /** 统计某文件在某应用下的引用数（删除保护判据）。 */
    long countByFile(String appCode, String fileId);
}
