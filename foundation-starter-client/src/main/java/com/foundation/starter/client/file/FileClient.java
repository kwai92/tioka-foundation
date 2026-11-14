package com.foundation.starter.client.file;

/**
 * 文件服务客户端（F-09 BAS-17 §接口 {@code /api/fdn/files}）：应用侧经 Foundation 上传与下载。
 *
 * <p>所有调用的 {@code appCode} 由 Foundation 依当前身份解析，客户端**不传应用编码**，
 * 隔离由服务端保证；上传与删除会写 Foundation 审计。
 */
public interface FileClient {

    /** 小文件直传（{@code multipart/form-data}），返回 {@code {fileId, sha256, size}}。 */
    FileUploadResult upload(String fileName, String contentType, byte[] content, String dataLabel);

    /** 读元数据（按应用隔离）。 */
    FileMetadata metadata(String fileId);

    /** 流式下载内容（服务端代理；{@code INFECTED} 文件拒绝）。 */
    byte[] download(String fileId);

    /** 颁发短时预签名下载地址（TTL 由服务端配置）。 */
    SignedDownloadUrl signedUrl(String fileId, String disposition);

    /** 逻辑删除；仍被业务对象引用时服务端返回 409。 */
    void delete(String fileId);
}
