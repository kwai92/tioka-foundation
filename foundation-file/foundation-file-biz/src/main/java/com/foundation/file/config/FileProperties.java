package com.foundation.file.config;

import com.foundation.file.domain.StorageKind;
import com.foundation.file.storage.StorageSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 文件服务配置（F-09 BAS-17 §配置项 {@code foundation.file.*}）。
 *
 * <p>与 {@code FileAutoConfiguration} 同包（00-施工总纲 §2.5），全部带默认值：
 * 默认本地磁盘 + 占位扫描，开箱即用；切对象存储只改 {@code foundation.file.storage.*}。
 */
@ConfigurationProperties(prefix = "foundation.file")
public class FileProperties {

    /** 病毒扫描挂点实现：本期仅 {@code placeholder}（{@code NoopVirusScanHook}）。 */
    private String virusScan = "placeholder";

    /** 预签名下载有效期（缺省 10 分钟）。 */
    private Duration signedUrlTtl = Duration.ofMinutes(10);

    /** 本地后端下载令牌签名密钥；为空时进程内随机生成（重启后旧链接失效）。 */
    private String signingSecret;

    /** 对外基地址：本地后端颁发「Foundation 自身短时地址」时使用。 */
    private String publicBaseUrl = "http://localhost:8080";

    private Multipart multipart = new Multipart();

    private Storage storage = new Storage();

    public String getVirusScan() {
        return virusScan;
    }

    public void setVirusScan(String virusScan) {
        this.virusScan = virusScan;
    }

    public Duration getSignedUrlTtl() {
        return signedUrlTtl;
    }

    public void setSignedUrlTtl(Duration signedUrlTtl) {
        this.signedUrlTtl = signedUrlTtl;
    }

    public String getSigningSecret() {
        return signingSecret;
    }

    public void setSigningSecret(String signingSecret) {
        this.signingSecret = signingSecret;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public Multipart getMultipart() {
        return multipart;
    }

    public void setMultipart(Multipart multipart) {
        this.multipart = multipart;
    }

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    /** 绑定为存储抽象所需的值对象（ADR-026）。 */
    public StorageSettings toStorageSettings() {
        return new StorageSettings(StorageKind.of(storage.kind), storage.localRoot, storage.endpoint,
                storage.bucket, storage.region, storage.accessKey, storage.secretKey, storage.pathStyle);
    }

    /** 分片上传配置（{@code foundation.file.multipart.*}）。 */
    public static class Multipart {

        /** 超过该阈值建议走分片（客户端提示用，缺省 5 MB）。 */
        private int thresholdMb = 5;

        /** 单个分片字节数（缺省 8 MB；S3 兼容后端要求不低于 5 MB）。 */
        private int partSizeMb = 8;

        /** 分片上传会话有效期（缺省 1 小时）。 */
        private Duration uploadTtl = Duration.ofHours(1);

        /** 单个分片字节数。 */
        public long partSizeBytes() {
            return (long) partSizeMb * 1024L * 1024L;
        }

        public int getThresholdMb() {
            return thresholdMb;
        }

        public void setThresholdMb(int thresholdMb) {
            this.thresholdMb = thresholdMb;
        }

        public int getPartSizeMb() {
            return partSizeMb;
        }

        public void setPartSizeMb(int partSizeMb) {
            this.partSizeMb = partSizeMb;
        }

        public Duration getUploadTtl() {
            return uploadTtl;
        }

        public void setUploadTtl(Duration uploadTtl) {
            this.uploadTtl = uploadTtl;
        }
    }

    /** 存储后端配置（{@code foundation.file.storage.*}）。 */
    public static class Storage {

        /** {@code LOCAL} 或 {@code S3}。 */
        private String kind = "LOCAL";

        /** 本地后端根目录。 */
        private String localRoot = "data/files";

        /** S3 兼容端点，如 {@code http://minio:9000}。 */
        private String endpoint;

        /** 桶名。 */
        private String bucket;

        /** 区域（SigV4 签名用，MinIO 常用 {@code us-east-1}）。 */
        private String region = "us-east-1";

        private String accessKey;

        private String secretKey;

        /** 是否使用 path-style（MinIO 多为 {@code true}）。 */
        private boolean pathStyle = true;

        public String getKind() {
            return kind;
        }

        public void setKind(String kind) {
            this.kind = kind;
        }

        public String getLocalRoot() {
            return localRoot;
        }

        public void setLocalRoot(String localRoot) {
            this.localRoot = localRoot;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public boolean isPathStyle() {
            return pathStyle;
        }

        public void setPathStyle(boolean pathStyle) {
            this.pathStyle = pathStyle;
        }
    }
}
