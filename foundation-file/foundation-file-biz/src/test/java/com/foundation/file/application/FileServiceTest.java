package com.foundation.file.application;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.file.api.FileContent;
import com.foundation.file.api.FileUploadCmd;
import com.foundation.file.api.FileUploadResult;
import com.foundation.file.api.FileView;
import com.foundation.file.api.SignedUrlView;
import com.foundation.file.config.FileProperties;
import com.foundation.file.domain.FileRef;
import com.foundation.file.domain.FileStatus;
import com.foundation.file.domain.ScanStatus;
import com.foundation.file.domain.Sha256;
import com.foundation.file.domain.StorageKind;
import com.foundation.file.scan.NoopVirusScanHook;
import com.foundation.file.storage.UrlSigner;
import com.foundation.file.support.InMemoryFileRefRepository;
import com.foundation.file.support.InMemoryFileRepository;
import com.foundation.file.support.InMemoryStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 文件应用服务（F-09 BAS-17 §接口 {@code /api/fdn/files}）：上传 / 下载 / 隔离 / 扫描 / 删除保护。 */
class FileServiceTest {

    private static final String APP = "app_foundation";

    private final InMemoryFileRepository files = new InMemoryFileRepository();
    private final InMemoryFileRefRepository refs = new InMemoryFileRefRepository();
    private final InMemoryStorage storage = new InMemoryStorage(StorageKind.LOCAL, false);
    private final FileProperties properties = new FileProperties();

    private SignedUrlService signedUrls;
    private FileService service;

    @BeforeEach
    void setUp() {
        UrlSigner signer = new UrlSigner("test-secret");
        signedUrls = new SignedUrlService(storage, signer, properties);
        MultipartUploadService multipart = new MultipartUploadService(files, storage, properties);
        service = new FileService(files, refs, storage, new NoopVirusScanHook(), signedUrls, multipart);
    }

    @Test
    void uploadThenMetadataExposesNormalizedFields() {
        byte[] content = "合同正文".getBytes(StandardCharsets.UTF_8);

        FileUploadResult result = service.upload(uploadCmd("合同.pdf", "application/pdf", null), content, "u1");
        FileView view = service.metadata(APP, result.fileId());

        assertThat(result.sha256()).isEqualTo(Sha256.hex(content));
        assertThat(result.size()).isEqualTo(content.length);
        assertThat(view.id()).startsWith("fil_");
        assertThat(view.appCode()).isEqualTo(APP);
        assertThat(view.fileName()).isEqualTo("合同.pdf");
        assertThat(view.contentType()).isEqualTo("application/pdf");
        assertThat(view.sizeBytes()).isEqualTo(content.length);
        assertThat(view.sha256()).isEqualTo(Sha256.hex(content));
        assertThat(view.storageKind()).isEqualTo("LOCAL");
        assertThat(view.dataLabel()).isEqualTo("INTERNAL");
        assertThat(view.scanStatus()).isEqualTo("SKIPPED");
        assertThat(view.status()).isEqualTo("ACTIVE");
        assertThat(view.uploadedBy()).isEqualTo("u1");
        assertThat(storage.object(APP + "/" + result.fileId())).isEqualTo(content);
    }

    @Test
    void uploadWithBlankContentTypeFallsBackToOctetStream() {
        FileUploadResult result = service.upload(uploadCmd("blob.bin", "  ", null), new byte[]{1, 2, 3}, "u1");

        assertThat(service.metadata(APP, result.fileId()).contentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void uploadWithExplicitDataLabelIsKept() {
        FileUploadResult result = service.upload(
                uploadCmd("secret.txt", "text/plain", "SECRET"), "x".getBytes(StandardCharsets.UTF_8), "u1");

        assertThat(service.metadata(APP, result.fileId()).dataLabel()).isEqualTo("SECRET");
    }

    @Test
    void uploadWithUnknownDataLabelFallsBackToInternal() {
        FileUploadResult result = service.upload(
                uploadCmd("a.txt", "text/plain", "unknown"), "x".getBytes(StandardCharsets.UTF_8), "u1");

        assertThat(service.metadata(APP, result.fileId()).dataLabel()).isEqualTo("INTERNAL");
    }

    @Test
    void uploadWithoutAppCodeIsRejected() {
        assertThatThrownBy(() -> service.upload(
                new FileUploadCmd(null, "a.txt", "text/plain", null, null, null),
                "x".getBytes(StandardCharsets.UTF_8), "u1"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void uploadWithBlankFileNameIsRejected() {
        assertThatThrownBy(() -> service.upload(uploadCmd("  ", "text/plain", null), new byte[0], "u1"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void downloadReturnsContentAndMetadata() throws IOException {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        FileUploadResult result = service.upload(uploadCmd("a.txt", "text/plain", null), content, "u1");

        FileContent downloaded = service.download(APP, result.fileId());

        assertThat(downloaded.fileName()).isEqualTo("a.txt");
        assertThat(downloaded.contentType()).isEqualTo("text/plain");
        assertThat(downloaded.dataLabel()).isEqualTo("INTERNAL");
        assertThat(downloaded.sizeBytes()).isEqualTo(content.length);
        try (InputStream in = downloaded.stream()) {
            assertThat(in.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void metadataOfOtherAppIsNotFound() {
        FileUploadResult result = service.upload(uploadCmd("a.txt", "text/plain", null), new byte[]{1}, "u1");

        assertThatThrownBy(() -> service.metadata("app_other", result.fileId()))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void downloadOfMissingFileIsNotFound() {
        assertThatThrownBy(() -> service.download(APP, "fil_missing"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void infectedFileCannotBeDownloaded() {
        FileUploadResult result = service.upload(
                uploadCmd("virus.bin", "application/octet-stream", null), new byte[]{9}, "u1");
        files.updateScanStatus(result.fileId(), ScanStatus.INFECTED, "scanner");

        assertThatThrownBy(() -> service.download(APP, result.fileId()))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void deleteWithoutRefsMarksDeletedAndHidesDownload() {
        FileUploadResult result = service.upload(uploadCmd("a.txt", "text/plain", null), new byte[]{1}, "u1");

        service.delete(APP, result.fileId());

        assertThat(files.statusOf(result.fileId())).contains(FileStatus.DELETED);
        assertThat(service.metadata(APP, result.fileId()).status()).isEqualTo("DELETED");
        assertThatThrownBy(() -> service.download(APP, result.fileId()))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void deleteIsIdempotent() {
        FileUploadResult result = service.upload(uploadCmd("a.txt", "text/plain", null), new byte[]{1}, "u1");

        service.delete(APP, result.fileId());
        service.delete(APP, result.fileId());

        assertThat(files.statusOf(result.fileId())).contains(FileStatus.DELETED);
    }

    @Test
    void deleteWithRefIsConflictAndKeepsFileActive() {
        FileUploadResult result = service.upload(uploadCmd("a.txt", "text/plain", null), new byte[]{1}, "u1");
        refs.insert(new FileRef("frf_1", result.fileId(), APP, "contract", "c1",
                LocalDateTime.now(ZoneOffset.UTC)));

        assertThatThrownBy(() -> service.delete(APP, result.fileId()))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
        assertThat(files.statusOf(result.fileId())).contains(FileStatus.ACTIVE);
    }

    @Test
    void signedUrlFallsBackToFoundationEndpointForLocalBackend() {
        FileUploadResult result = service.upload(uploadCmd("a.txt", "text/plain", null), new byte[]{1}, "u1");

        SignedUrlView view = service.signedUrl(APP, result.fileId(), null);

        assertThat(view.method()).isEqualTo("GET");
        assertThat(view.url()).startsWith("http://localhost:8080/api/fdn/files/signed/");
        assertThat(view.expiresAt()).isAfter(LocalDateTime.now(ZoneOffset.UTC));
    }

    @Test
    void signedUrlTokenVerifiesToSameFile() {
        FileUploadResult result = service.upload(uploadCmd("a.txt", "text/plain", null), new byte[]{1}, "u1");
        SignedUrlView view = service.signedUrl(APP, result.fileId(), null);
        String token = view.url().substring(view.url().lastIndexOf('/') + 1);

        Optional<UrlSigner.SignedRef> verified = signedUrls.verify(token);

        assertThat(verified).isPresent();
        assertThat(verified.orElseThrow().appCode()).isEqualTo(APP);
        assertThat(verified.orElseThrow().fileId()).isEqualTo(result.fileId());
        assertThat(verified.orElseThrow().expiresAt()).isAfter(Instant.now());
    }

    @Test
    void signedUrlOfInfectedFileIsForbidden() {
        FileUploadResult result = service.upload(uploadCmd("virus.bin", "application/octet-stream", null),
                new byte[]{9}, "u1");
        files.updateScanStatus(result.fileId(), ScanStatus.INFECTED, "scanner");

        assertThatThrownBy(() -> service.signedUrl(APP, result.fileId(), null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    private static FileUploadCmd uploadCmd(String fileName, String contentType, String dataLabel) {
        return new FileUploadCmd(APP, fileName, contentType, dataLabel, null, null);
    }
}
