package com.foundation.file.application;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.file.domain.FileRules;
import com.foundation.file.domain.StoredFile;
import com.foundation.file.infrastructure.FileRepository;

/**
 * 文件读取的隔离判定（F-09 BAS-17）：不存在与跨应用一律同一个 {@code NOT_FOUND(40400)}，
 * 不暴露文件在其他应用下的存在性。
 */
final class FileLookup {

    private FileLookup() {
    }

    /** 按应用隔离取文件；不存在、ID 为空或跨应用都抛 {@code NOT_FOUND}。 */
    static StoredFile require(FileRepository files, String appCode, String fileId) {
        String app = FileRules.requireAppCode(appCode);
        if (fileId == null || fileId.isBlank()) {
            throw new BizException(ErrorCode.NOT_FOUND, "文件不存在");
        }
        return files.find(fileId)
                .filter(file -> file.belongsTo(app))
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "文件不存在"));
    }

    /** 引用必须同时给出业务对象类型与 ID（唯一四元组的另一半）。 */
    static void requireBiz(String bizType, String bizId) {
        if (bizType == null || bizType.isBlank() || bizId == null || bizId.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "业务对象类型与 ID 不能为空");
        }
    }
}
