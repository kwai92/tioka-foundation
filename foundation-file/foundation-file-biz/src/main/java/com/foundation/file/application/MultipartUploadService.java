package com.foundation.file.application;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.id.Ids;
import com.foundation.file.api.FileUploadResult;
import com.foundation.file.api.MultipartCompleteCmd;
import com.foundation.file.api.MultipartInitCmd;
import com.foundation.file.api.MultipartInitView;
import com.foundation.file.api.PresignedPartView;
import com.foundation.file.api.UploadedPart;
import com.foundation.file.config.FileProperties;
import com.foundation.file.domain.DataLabel;
import com.foundation.file.domain.FileIdPrefix;
import com.foundation.file.domain.FileRules;
import com.foundation.file.domain.FileStatus;
import com.foundation.file.domain.ScanStatus;
import com.foundation.file.domain.Sha256;
import com.foundation.file.domain.StoredFile;
import com.foundation.file.infrastructure.FileRepository;
import com.foundation.file.storage.ObjectStorageAdapter;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 分片上传（F-09 BAS-17）：超阈值大文件先取分片地址，再合并并校验 {@code sha256}。
 *
 * <p>对象存储后端的分片经预签名 URL 直传，服务端只做合并收口；
 * 本地后端没有可直连的存储，分片由 Foundation 自身端点（{@code PUT /api/fdn/files/multipart:part}）接收。
 *
 * <p>{@code uploadId} 为 {@code {fileId}~{nativeUploadId}}：前缀定位对象键，后缀交后端合并。
 */
public class MultipartUploadService {

    private static final char SEPARATOR = '~';

    private final FileRepository files;
    private final ObjectStorageAdapter storage;
    private final FileProperties properties;

    public MultipartUploadService(FileRepository files, ObjectStorageAdapter storage, FileProperties properties) {
        this.files = files;
        this.storage = storage;
        this.properties = properties;
    }

    /** 初始化分片会话：返回上传标识与每个分片的上传地址。 */
    public MultipartInitView init(MultipartInitCmd cmd) {
        String appCode = FileRules.requireAppCode(cmd.appCode());
        FileRules.requireUpload(cmd.fileName(), cmd.sizeBytes());
        String contentType = FileRules.normalizeContentType(cmd.contentType());
        String fileId = Ids.newId(FileIdPrefix.FILE);
        String key = FileRules.storageKey(appCode, fileId);
        String nativeUploadId = storage.initMultipart(key, contentType);
        String uploadId = fileId + SEPARATOR + (nativeUploadId == null ? "" : nativeUploadId);

        long partSize = properties.getMultipart().partSizeBytes();
        Duration ttl = properties.getMultipart().getUploadTtl();
        int count = FileRules.partCount(cmd.sizeBytes(), partSize);
        List<PresignedPartView> parts = new ArrayList<>(count);
        for (int partNumber = 1; partNumber <= count; partNumber++) {
            int number = partNumber;
            String url = storage.presignedUploadPart(key, nativeUploadId, number, ttl)
                    .orElseGet(() -> partUploadUrl(uploadId, number));
            parts.add(new PresignedPartView(number, url));
        }
        return new MultipartInitView(uploadId, partSize, parts);
    }

    /** 接收一个分片（仅本地后端；对象存储后端的分片由预签名 URL 直传）。 */
    public void putPart(String appCode, String uploadId, int partNumber, byte[] content) {
        String app = FileRules.requireAppCode(appCode);
        UploadRef ref = parse(uploadId);
        if (partNumber < 1) {
            throw new BizException(ErrorCode.BAD_REQUEST, "分片序号必须从 1 起");
        }
        storage.putPart(FileRules.storageKey(app, ref.fileId()), partNumber, content);
    }

    /** 合并分片、校验内容摘要并登记 {@code fdn_file}。 */
    public FileUploadResult complete(MultipartCompleteCmd cmd) {
        String appCode = FileRules.requireAppCode(cmd.appCode());
        FileRules.requireUpload(cmd.fileName(), cmd.sizeBytes());
        UploadRef ref = parse(cmd.uploadId());
        String key = FileRules.storageKey(appCode, ref.fileId());
        List<UploadedPart> parts = cmd.parts() == null ? List.of() : cmd.parts();
        storage.completeMultipart(key, ref.nativeUploadId(), parts);

        String sha256;
        try (InputStream stream = storage.openStream(key)) {
            sha256 = Sha256.hex(stream);
        } catch (java.io.IOException ex) {
            throw new BizException(ErrorCode.INTERNAL, "读取合并结果失败：" + ex.getMessage());
        }
        if (cmd.sha256() != null && !cmd.sha256().isBlank()) {
            Sha256.requireHex(cmd.sha256());
            if (!Sha256.constantTimeEquals(sha256, cmd.sha256())) {
                storage.delete(key);
                throw new BizException(ErrorCode.BAD_REQUEST, "sha256 校验不一致");
            }
        }

        String contentType = FileRules.normalizeContentType(cmd.contentType());
        String operator = operator();
        // 分片路径内容不驻留内存：扫描挂点在并合后对对象执行；占位实现统一落 SKIPPED。
        StoredFile file = new StoredFile(ref.fileId(), appCode, blankToNull(cmd.bizType()),
                blankToNull(cmd.bizId()), cmd.fileName(), contentType, cmd.sizeBytes(), sha256,
                storage.kind(), key, DataLabel.of(cmd.dataLabel()), ScanStatus.SKIPPED, FileStatus.ACTIVE,
                operator, null, null);
        files.insert(file, operator);
        return new FileUploadResult(ref.fileId(), sha256, cmd.sizeBytes());
    }

    private String partUploadUrl(String uploadId, int partNumber) {
        return properties.getPublicBaseUrl() + "/api/fdn/files/multipart:part?uploadId=" + uploadId
                + "&partNumber=" + partNumber;
    }

    private static UploadRef parse(String uploadId) {
        int separator = uploadId == null ? -1 : uploadId.indexOf(SEPARATOR);
        if (separator <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "uploadId 不合法");
        }
        return new UploadRef(uploadId.substring(0, separator), uploadId.substring(separator + 1));
    }

    private static String operator() {
        return PrincipalHolder.current().map(Principal::userId).orElse("system");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** {@code uploadId} 解码结果：文件 ID 与后端原生上传标识。 */
    private record UploadRef(String fileId, String nativeUploadId) {
    }
}
