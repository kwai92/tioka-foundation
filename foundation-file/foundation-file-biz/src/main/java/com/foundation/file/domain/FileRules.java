package com.foundation.file.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

/**
 * 文件领域规则（F-09 BAS-17）：入参规范化与不变量校验。
 *
 * <p>不依赖 Web / 安全 / 持久化上下文，供应用服务与控制器共用。
 */
public final class FileRules {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private static final int MAX_FILE_NAME = 512;

    private static final int MAX_CONTENT_TYPE = 128;

    private FileRules() {
    }

    /** 上传入参校验：文件名必填且不超长，大小非负。 */
    public static void requireUpload(String fileName, long sizeBytes) {
        if (fileName == null || fileName.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "文件名不能为空");
        }
        if (fileName.length() > MAX_FILE_NAME) {
            throw new BizException(ErrorCode.BAD_REQUEST, "文件名过长");
        }
        if (sizeBytes < 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "文件大小不能为负");
        }
    }

    /** 应用编码必填（隔离边界，任何读写都不得为空）。 */
    public static String requireAppCode(String appCode) {
        if (appCode == null || appCode.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "应用编码不能为空");
        }
        return appCode;
    }

    /** MIME 类型规范化：空值取 {@code application/octet-stream}。 */
    public static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return DEFAULT_CONTENT_TYPE;
        }
        String trimmed = contentType.trim();
        return trimmed.length() > MAX_CONTENT_TYPE ? trimmed.substring(0, MAX_CONTENT_TYPE) : trimmed;
    }

    /** 对象键（不含桶）：{@code {appCode}/{fileId}}，天然按应用分目录。 */
    public static String storageKey(String appCode, String fileId) {
        return requireAppCode(appCode) + "/" + fileId;
    }

    /** 按分片阈值切分片数（不足一片按一片处理）。 */
    public static int partCount(long sizeBytes, long partSizeBytes) {
        if (partSizeBytes <= 0) {
            throw new BizException(ErrorCode.INTERNAL, "分片大小必须为正");
        }
        if (sizeBytes <= 0) {
            return 1;
        }
        return (int) ((sizeBytes + partSizeBytes - 1) / partSizeBytes);
    }
}
