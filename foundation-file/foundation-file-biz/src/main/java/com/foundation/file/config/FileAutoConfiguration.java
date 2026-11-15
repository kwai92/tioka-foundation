package com.foundation.file.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.foundation.file.api.FileApi;
import com.foundation.file.api.FileController;
import com.foundation.file.api.FileRefApi;
import com.foundation.file.application.FileRefService;
import com.foundation.file.application.FileService;
import com.foundation.file.application.MultipartUploadService;
import com.foundation.file.application.SignedUrlService;
import com.foundation.file.domain.StorageKind;
import com.foundation.file.infrastructure.FileRefRepository;
import com.foundation.file.infrastructure.FileRepository;
import com.foundation.file.infrastructure.MybatisFileRefRepository;
import com.foundation.file.infrastructure.MybatisFileRepository;
import com.foundation.file.infrastructure.mapper.FileMapper;
import com.foundation.file.infrastructure.mapper.FileRefMapper;
import com.foundation.file.scan.NoopVirusScanHook;
import com.foundation.file.scan.VirusScanHook;
import com.foundation.file.storage.LocalStorageAdapter;
import com.foundation.file.storage.ObjectStorageAdapter;
import com.foundation.file.storage.S3StorageAdapter;
import com.foundation.file.storage.StorageSettings;
import com.foundation.file.storage.UrlSigner;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 文件服务自动装配（F-09 BAS-17）。
 *
 * <p>装配链路：Mapper（由 {@code foundation-starter-data} 的 {@code @MapperScan} 登记）
 * → Repository（MyBatis-Plus，48-CS §4）→ 存储抽象 / 扫描挂点（ADR-026 SPI）
 * → 应用服务 → {@code -api} 契约 Bean → Controller。
 *
 * <p>依赖 {@link SqlSessionFactory}（持久化），缺失时整块跳过；存储后端由
 * {@code foundation.file.storage.kind} 选择（默认本地磁盘，可切 S3 兼容对象存储）。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "foundation.server.domains", name = "file", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(name = "com.foundation.starter.data.config.FoundationDataAutoConfiguration")
@ConditionalOnBean(SqlSessionFactory.class)
@EnableConfigurationProperties(FileProperties.class)
public class FileAutoConfiguration {

    // ---------- 仓储（MyBatis-Plus） ----------

    @Bean
    @ConditionalOnMissingBean
    public FileRepository fileRepository(FileMapper mapper) {
        return new MybatisFileRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public FileRefRepository fileRefRepository(FileRefMapper mapper) {
        return new MybatisFileRefRepository(mapper);
    }

    // ---------- 存储抽象与扫描挂点（ADR-026 / F-09 BAS-17） ----------

    @Bean
    @ConditionalOnMissingBean
    public ObjectStorageAdapter objectStorageAdapter(FileProperties properties) {
        StorageSettings settings = properties.toStorageSettings();
        return settings.kind() == StorageKind.S3 ? new S3StorageAdapter(settings) : new LocalStorageAdapter(settings);
    }

    @Bean
    @ConditionalOnMissingBean
    public VirusScanHook virusScanHook() {
        return new NoopVirusScanHook();
    }

    @Bean
    @ConditionalOnMissingBean
    public UrlSigner fileUrlSigner(FileProperties properties) {
        return new UrlSigner(properties.getSigningSecret());
    }

    // ---------- 应用服务 ----------

    @Bean
    @ConditionalOnMissingBean
    public SignedUrlService signedUrlService(ObjectStorageAdapter storage, UrlSigner signer,
                                             FileProperties properties) {
        return new SignedUrlService(storage, signer, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public MultipartUploadService multipartUploadService(FileRepository files, ObjectStorageAdapter storage,
                                                        FileProperties properties) {
        return new MultipartUploadService(files, storage, properties);
    }

    @Bean
    @ConditionalOnMissingBean(FileApi.class)
    public FileApi fileApi(FileRepository files, FileRefRepository refs, ObjectStorageAdapter storage,
                           VirusScanHook scanner, SignedUrlService signedUrls,
                           MultipartUploadService multipart) {
        return new FileService(files, refs, storage, scanner, signedUrls, multipart);
    }

    @Bean
    @ConditionalOnMissingBean(FileRefApi.class)
    public FileRefApi fileRefApi(FileRepository files, FileRefRepository refs) {
        return new FileRefService(files, refs);
    }

    // ---------- REST 控制器 ----------

    @Bean
    @ConditionalOnMissingBean
    public FileController fileController(FileApi files, FileRefApi refs, MultipartUploadService multipart,
                                         SignedUrlService signedUrls) {
        return new FileController(files, refs, multipart, signedUrls);
    }
}
