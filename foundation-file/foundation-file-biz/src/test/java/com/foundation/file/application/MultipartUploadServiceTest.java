package com.foundation.file.application;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.file.api.FileUploadResult;
import com.foundation.file.api.MultipartCompleteCmd;
import com.foundation.file.api.MultipartInitCmd;
import com.foundation.file.api.MultipartInitView;
import com.foundation.file.api.PresignedPartView;
import com.foundation.file.api.UploadedPart;
import com.foundation.file.config.FileProperties;
import com.foundation.file.domain.FileStatus;
import com.foundation.file.domain.Sha256;
import com.foundation.file.domain.StorageKind;
import com.foundation.file.support.InMemoryFileRepository;
import com.foundation.file.support.InMemoryStorage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 分片上传（F-09 BAS-17）：分片切分、预签名 / 回落地址、合并与 sha256 校验。 */
class MultipartUploadServiceTest {

    private static final String APP = "app_foundation";

    private static final long MB = 1024L * 1024L;

    @Test
    void localBackendSplitsPartsAndFallsBackToFoundationEndpoint() {
        InMemoryStorage storage = new InMemoryStorage(StorageKind.LOCAL, false);
        MultipartUploadService service =
                new MultipartUploadService(new InMemoryFileRepository(), storage, properties(8));

        MultipartInitView view = service.init(
                new MultipartInitCmd(APP, "big.bin", 20 * MB, "application/octet-stream", null, null, null));

        assertThat(view.partSize()).isEqualTo(8 * MB);
        assertThat(view.parts()).hasSize(3);
        assertThat(view.parts()).extracting(PresignedPartView::partNumber).containsExactly(1, 2, 3);
        assertThat(view.uploadId()).contains("~");
        for (PresignedPartView part : view.parts()) {
            assertThat(part.url()).startsWith("http://localhost:8080/api/fdn/files/multipart:part?uploadId=");
            assertThat(part.url()).contains("partNumber=" + part.partNumber());
        }
    }

    @Test
    void s3BackendUsesPresignedPartUrls() {
        InMemoryStorage storage = new InMemoryStorage(StorageKind.S3, true);
        MultipartUploadService service =
                new MultipartUploadService(new InMemoryFileRepository(), storage, properties(8));

        MultipartInitView view = service.init(new MultipartInitCmd(APP, "big.bin", 10 * MB, null, null, null, null));

        assertThat(view.parts()).hasSize(2);
        assertThat(view.parts()).allSatisfy(part -> assertThat(part.url()).startsWith("https://s3.test/"));
    }

    @Test
    void emptySizeYieldsSinglePart() {
        MultipartUploadService service = new MultipartUploadService(new InMemoryFileRepository(),
                new InMemoryStorage(StorageKind.LOCAL, false), properties(8));

        MultipartInitView view = service.init(new MultipartInitCmd(APP, "empty.bin", 0, null, null, null, null));

        assertThat(view.parts()).hasSize(1);
    }

    @Test
    void putPartThenCompleteMergesAndRegistersFile() {
        InMemoryFileRepository files = new InMemoryFileRepository();
        InMemoryStorage storage = new InMemoryStorage(StorageKind.LOCAL, false);
        MultipartUploadService service = new MultipartUploadService(files, storage, properties(8));
        byte[] merged = "abcdef".getBytes(StandardCharsets.UTF_8);

        MultipartInitView init = service.init(
                new MultipartInitCmd(APP, "big.bin", 6, "application/octet-stream", null, null, null));
        service.putPart(APP, init.uploadId(), 1, "abc".getBytes(StandardCharsets.UTF_8));
        service.putPart(APP, init.uploadId(), 2, "def".getBytes(StandardCharsets.UTF_8));

        FileUploadResult result = service.complete(new MultipartCompleteCmd(APP, init.uploadId(), "big.bin",
                6, "application/octet-stream", Sha256.hex(merged), null, null, null,
                List.of(new UploadedPart(1, "e1"), new UploadedPart(2, "e2"))));

        assertThat(result.fileId()).isEqualTo(fileIdOf(init.uploadId()));
        assertThat(result.sha256()).isEqualTo(Sha256.hex(merged));
        assertThat(result.size()).isEqualTo(6);
        assertThat(files.statusOf(result.fileId())).contains(FileStatus.ACTIVE);
        assertThat(storage.object(APP + "/" + result.fileId())).isEqualTo(merged);
    }

    @Test
    void completeWithMismatchedSha256RejectsAndRemovesObject() {
        InMemoryFileRepository files = new InMemoryFileRepository();
        InMemoryStorage storage = new InMemoryStorage(StorageKind.LOCAL, false);
        MultipartUploadService service = new MultipartUploadService(files, storage, properties(8));

        MultipartInitView init = service.init(new MultipartInitCmd(APP, "big.bin", 3, null, null, null, null));
        service.putPart(APP, init.uploadId(), 1, "abc".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.complete(new MultipartCompleteCmd(APP, init.uploadId(), "big.bin",
                3, null, Sha256.hex("zzz".getBytes(StandardCharsets.UTF_8)), null, null, null,
                List.of(new UploadedPart(1, "e1")))))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);

        assertThat(storage.object(APP + "/" + fileIdOf(init.uploadId()))).isNull();
        assertThat(files.statusOf(fileIdOf(init.uploadId()))).isEmpty();
    }

    @Test
    void completeWithMalformedUploadIdIsRejected() {
        MultipartUploadService service = new MultipartUploadService(new InMemoryFileRepository(),
                new InMemoryStorage(StorageKind.LOCAL, false), properties(8));

        assertThatThrownBy(() -> service.complete(new MultipartCompleteCmd(APP, "no-separator", "big.bin",
                1, null, null, null, null, null, List.of())))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void putPartWithInvalidPartNumberIsRejected() {
        MultipartUploadService service = new MultipartUploadService(new InMemoryFileRepository(),
                new InMemoryStorage(StorageKind.LOCAL, false), properties(8));
        MultipartInitView init = service.init(new MultipartInitCmd(APP, "big.bin", 3, null, null, null, null));

        assertThatThrownBy(() -> service.putPart(APP, init.uploadId(), 0, new byte[]{1}))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    private static String fileIdOf(String uploadId) {
        return uploadId.substring(0, uploadId.indexOf('~'));
    }

    private static FileProperties properties(int partSizeMb) {
        FileProperties properties = new FileProperties();
        properties.getMultipart().setPartSizeMb(partSizeMb);
        return properties;
    }
}
