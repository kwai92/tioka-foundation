package com.foundation.file.api;

/**
 * 文件服务契约（F-09 BAS-17、S15 §14.1「公共服务」组）。
 *
 * <p>上传、元数据、下载、预签名、分片与删除；所有方法都以 {@code appCode} 为隔离边界，
 * 跨应用访问一律 {@code NOT_FOUND(40400)}（不暴露存在性）。
 *
 * <p>调用方（Controller / SDK）负责把当前身份解析成 {@code appCode}
 * 与操作者标识（{@code operator}）后传入，本契约不依赖 Web 或安全上下文。
 */
public interface FileApi {

    /** 小文件直传：写入对象存储并登记 {@code fdn_file}。 */
    FileUploadResult upload(FileUploadCmd cmd, byte[] content, String operator);

    /** 按应用隔离读取元数据。 */
    FileView metadata(String appCode, String fileId);

    /** 按应用隔离下载内容（调用方负责关闭 {@link FileContent#stream()}）。 */
    FileContent download(String appCode, String fileId);

    /** 颁发短时预签名下载 URL（TTL 见 {@code foundation.file.signed-url-ttl}）。 */
    SignedUrlView signedUrl(String appCode, String fileId, String disposition);

    /** 逻辑删除：有 {@code fdn_file_ref} 引用时抛 {@code CONFLICT(40900)}。 */
    void delete(String appCode, String fileId);

    /** 分片上传初始化：返回上传会话标识与分片预签名地址。 */
    MultipartInitView multipartInit(MultipartInitCmd cmd);

    /** 分片合并并校验 {@code sha256}，登记 {@code fdn_file}。 */
    FileUploadResult multipartComplete(MultipartCompleteCmd cmd);
}
