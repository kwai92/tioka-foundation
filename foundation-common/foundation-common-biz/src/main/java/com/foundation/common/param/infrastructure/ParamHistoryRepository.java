package com.foundation.common.param.infrastructure;

/**
 * 参数变更历史仓储：只记旧值与新值的 SHA-256 摘要，不记旧明文（F-02 BAS-09）。
 */
public interface ParamHistoryRepository {

    void append(String paramId, String oldDigest, String newDigest, String operator);
}
