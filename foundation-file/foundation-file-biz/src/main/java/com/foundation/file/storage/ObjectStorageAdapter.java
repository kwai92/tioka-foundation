package com.foundation.file.storage;

import com.foundation.file.api.UploadedPart;
import com.foundation.file.domain.StorageKind;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 对象存储 SPI（ADR-026）：本地磁盘与 S3 兼容对象存储的统一切换点。
 *
 * <p>只暴露存储原语（读 / 写 / 删 / 分片 / 签名），不感知文件元数据与应用语义；
 * 预签名「下载 / 分片上传」返回空表示该后端不支持直连，由
 * {@link com.foundation.file.application.SignedUrlService} / {@code MultipartUploadService}
 * 回落到 Foundation 自身地址。
 */
public interface ObjectStorageAdapter {

    /** 后端类型。 */
    StorageKind kind();

    /** 写入对象。 */
    void put(String key, byte[] content, String contentType);

    /** 打开对象内容流（调用方负责关闭）。 */
    InputStream openStream(String key);

    /** 删除对象（不存在时幂等）。 */
    void delete(String key);

    /**
     * 发起分片上传。
     *
     * @return 后端原生上传标识（本地后端返回空串，由服务端临时目录承载分片）
     */
    String initMultipart(String key, String contentType);

    /** 分片上传地址（S3 为对象存储预签名 URL）；不支持直连时返回空。 */
    Optional<String> presignedUploadPart(String key, String nativeUploadId, int partNumber, Duration ttl);

    /**
     * 服务端接收一个分片（仅本地后端支持；S3 后端分片直传对象存储）。
     *
     * @param key        对象键
     * @param partNumber 分片序号（从 1 起）
     * @param content    分片内容
     */
    default void putPart(String key, int partNumber, byte[] content) {
        throw new UnsupportedOperationException("该存储后端不支持服务端接收分片");
    }

    /** 合并分片并落对象（本地后端在合并后清理临时分片目录）。 */
    void completeMultipart(String key, String nativeUploadId, List<UploadedPart> parts);

    /** 放弃分片并清理中间态。 */
    void abortMultipart(String key, String nativeUploadId);

    /** 直读对象存储的预签名下载地址；不支持时返回空。 */
    Optional<String> presignedDownload(String key, Duration ttl, String fileName, String contentType);
}
