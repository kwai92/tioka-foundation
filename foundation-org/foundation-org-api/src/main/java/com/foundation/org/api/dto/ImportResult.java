package com.foundation.org.api.dto;

import java.util.List;

/**
 * 导入结果（F-03 §4.1）：{@code {taskId, created, updated, skipped, errors[]}}。
 *
 * @param taskId  导入任务 ID（{@code GET /api/fdn/org/import/{taskId}} 查询）
 * @param created 新增行数
 * @param updated 更新行数
 * @param skipped 跳过行数（幂等重导时为数据行总数）
 * @param errors  逐行错误；非空表示整表拒绝、未落库
 */
public record ImportResult(String taskId,
                           int created,
                           int updated,
                           int skipped,
                           List<ImportError> errors) {

    /** 是否整表被拒绝（有错误即未落库）。 */
    public boolean rejected() {
        return errors != null && !errors.isEmpty();
    }
}
