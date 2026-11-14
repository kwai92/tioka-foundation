package com.foundation.file.application;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.id.Ids;
import com.foundation.file.api.FileApi;
import com.foundation.file.api.FileContent;
import com.foundation.file.api.FileUploadCmd;
import com.foundation.file.api.FileUploadResult;
import com.foundation.file.api.FileView;
import com.foundation.file.api.MultipartCompleteCmd;
import com.foundation.file.api.MultipartInitCmd;
import com.foundation.file.api.MultipartInitView;
import com.foundation.file.api.SignedUrlView;
import com.foundation.file.domain.DataLabel;
import com.foundation.file.domain.FileIdPrefix;
import com.foundation.file.domain.FileRules;
import com.foundation.file.domain.FileStatus;
import com.foundation.file.domain.ScanStatus;
import com.foundation.file.domain.Sha256;
import com.foundation.file.domain.StoredFile;
import com.foundation.file.infrastructure.FileRefRepository;
import com.foundation.file.infrastructure.FileRepository;
import com.foundation.file.scan.VirusScanHook;
import com.foundation.file.storage.ObjectStorageAdapter;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;

/**
 * 文件服务（F-09 BAS-17）：上传、元数据、下载、预签名与删除，所有读写以 {@code appCode} 为隔离边界。
 *
 * <p>删除语义对齐 ADR-029 第 13 条：仍被 {@code fdn_file_ref} 引用时抛 {@code CONFLICT(40900)}；
 * 无引用时逻辑删除（{@code status=DELETED}），对象由延迟清理回收。
 * {@code INFECTED} 文件禁止下载。
 */
public class FileService implements FileApi {

    private final FileRepository files;
    private final FileRefRepository refs;
    private final ObjectStorageAdapter storage;
    private final VirusScanHook scanner;
    private final SignedUrlService signedUrls;
    private final MultipartUploadService multipart;

    public FileService(FileRepository files,
                       FileRefRepository refs,
                       ObjectStorageAdapter storage,
                       VirusScanHook scanner,
                       SignedUrlService signedUrls,
                       MultipartUploadService multipart) {
        this.files = files;
        this.refs = refs;
        this.storage = storage;
        this.scanner = scanner;
        this.signedUrls = signedUrls;
        this.multipart = multipart;
    }

    @Override
    public FileUploadResult upload(FileUploadCmd cmd, byte[] content, String operator) {
        String appCode = FileRules.requireAppCode(cmd.appCode());
        byte[] bytes = content == null ? new byte[0] : content;
        FileRules.requireUpload(cmd.fileName(), bytes.length);
        String contentType = FileRules.normalizeContentType(cmd.contentType());
        String sha256 = Sha256.hex(bytes);
        String fileId = Ids.newId(FileIdPrefix.FILE);
        String key = FileRules.storageKey(appCode, fileId);
        ScanStatus scanStatus = scanner.scan(cmd.fileName(), bytes);

        storage.put(key, bytes, contentType);
        StoredFile file = new StoredFile(fileId, appCode, blankToNull(cmd.bizType()), blankToNull(cmd.bizId()),
                cmd.fileName(), contentType, bytes.length, sha256, storage.kind(), key,
                DataLabel.of(cmd.dataLabel()), scanStatus, FileStatus.ACTIVE, operator, null, null);
        files.insert(file, operator);
        return new FileUploadResult(fileId, sha256, bytes.length);
    }

    @Override
    public FileView metadata(String appCode, String fileId) {
        return toView(FileLookup.require(files, appCode, fileId));
    }

    @Override
    public FileContent download(String appCode, String fileId) {
        StoredFile file = requireDownloadable(appCode, fileId);
        return new FileContent(file.fileName(), file.contentType(), file.dataLabel().name(),
                file.sizeBytes(), storage.openStream(file.storageKey()));
    }

    @Override
    public SignedUrlView signedUrl(String appCode, String fileId, String disposition) {
        return signedUrls.issue(requireDownloadable(appCode, fileId), disposition);
    }

    @Override
    public void delete(String appCode, String fileId) {
        String app = FileRules.requireAppCode(appCode);
        StoredFile file = FileLookup.require(files, app, fileId);
        if (file.status() == FileStatus.DELETED) {
            return;
        }
        if (refs.countByFile(app, fileId) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "文件仍被业务对象引用，禁止删除");
        }
        files.markDeleted(fileId, operator());
    }

    @Override
    public MultipartInitView multipartInit(MultipartInitCmd cmd) {
        return multipart.init(cmd);
    }

    @Override
    public FileUploadResult multipartComplete(MultipartCompleteCmd cmd) {
        return multipart.complete(cmd);
    }

    private StoredFile requireDownloadable(String appCode, String fileId) {
        StoredFile file = FileLookup.require(files, appCode, fileId);
        if (file.status() != FileStatus.ACTIVE) {
            throw new BizException(ErrorCode.NOT_FOUND, "文件不存在");
        }
        if (!file.scanStatus().downloadable()) {
            throw new BizException(ErrorCode.FORBIDDEN, "文件未通过安全扫描，禁止下载");
        }
        return file;
    }

    private static FileView toView(StoredFile file) {
        return new FileView(file.id(), file.appCode(), file.fileName(), file.contentType(), file.sizeBytes(),
                file.sha256(), file.storageKind().name(), file.dataLabel().name(), file.scanStatus().name(),
                file.status().name(), file.bizType(), file.bizId(), file.uploadedBy(), file.createAt());
    }

    private static String operator() {
        return PrincipalHolder.current().map(Principal::userId).orElse("system");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
