package com.foundation.file.infrastructure;

import com.foundation.file.domain.FileStatus;
import com.foundation.file.domain.ScanStatus;
import com.foundation.file.domain.StoredFile;

import java.util.Optional;

/** 文件对象仓储（F-09 BAS-17）：持久化边界，实现走 MyBatis-Plus（48-CS §4）。 */
public interface FileRepository {

    /** 追加一行文件记录。 */
    void insert(StoredFile file, String operator);

    /** 按 ID 读取（不区分应用，隔离判定由应用服务负责）。 */
    Optional<StoredFile> find(String id);

    /** 逻辑删除（{@code status=DELETED}），对象延迟清理（ADR-029 第 13 条）。 */
    void markDeleted(String id, String operator);

    /** 回写扫描状态（扫描挂点接入后使用）。 */
    void updateScanStatus(String id, ScanStatus scanStatus, String operator);

    /** 读取当前状态（不存在返回空）。 */
    Optional<FileStatus> statusOf(String id);
}
