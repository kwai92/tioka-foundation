package com.foundation.authz.api;

import java.util.List;

/**
 * 自管子系统一次性导入契约（F-05 BAS-27 §自管导入）。
 *
 * <p>自管（{@code SELF}）子系统改为集中（{@code CONCENTRATED}）前，先 {@link #preview} 生成批次，
 * 再 {@link #apply} 落库；应用幂等。
 */
public interface SelfManagedImportApi {

    /**
     * 生成导入预览批次。
     *
     * @param appCode  应用编码
     * @param items    导入明细项
     * @param operator 操作人
     */
    SelfImportPreviewView preview(String appCode, List<SelfImportItemCmd> items, String operator);

    /**
     * 应用已预览批次（幂等）。
     *
     * @param batchId  批次 ID
     * @param operator 操作人
     */
    SelfImportApplyResult apply(String batchId, String operator);
}
