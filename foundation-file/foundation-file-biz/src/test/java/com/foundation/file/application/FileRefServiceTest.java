package com.foundation.file.application;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.file.api.FileRefCmd;
import com.foundation.file.api.FileRefView;
import com.foundation.file.api.FileUploadCmd;
import com.foundation.file.api.FileUploadResult;
import com.foundation.file.config.FileProperties;
import com.foundation.file.domain.StorageKind;
import com.foundation.file.scan.NoopVirusScanHook;
import com.foundation.file.storage.UrlSigner;
import com.foundation.file.support.InMemoryFileRefRepository;
import com.foundation.file.support.InMemoryFileRepository;
import com.foundation.file.support.InMemoryStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 文件引用服务（F-09 BAS-17）：四元组幂等、按应用隔离、解除引用。 */
class FileRefServiceTest {

    private static final String APP = "app_foundation";

    private final InMemoryFileRepository files = new InMemoryFileRepository();
    private final InMemoryFileRefRepository refs = new InMemoryFileRefRepository();
    private final InMemoryStorage storage = new InMemoryStorage(StorageKind.LOCAL, false);

    private FileService fileService;
    private FileRefService service;

    @BeforeEach
    void setUp() {
        FileProperties properties = new FileProperties();
        UrlSigner signer = new UrlSigner("test-secret");
        SignedUrlService signedUrls = new SignedUrlService(storage, signer, properties);
        MultipartUploadService multipart = new MultipartUploadService(files, storage, properties);
        fileService = new FileService(files, refs, storage, new NoopVirusScanHook(), signedUrls, multipart);
        service = new FileRefService(files, refs);
    }

    @Test
    void addRefIsIdempotentForSameQuadruple() {
        String fileId = upload();

        FileRefView first = service.addRef(fileId, new FileRefCmd(APP, "contract", "c1"));
        FileRefView second = service.addRef(fileId, new FileRefCmd(APP, "contract", "c1"));

        assertThat(first.id()).startsWith("frf_");
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(refs.countByFile(APP, fileId)).isEqualTo(1);
    }

    @Test
    void refsListsAllReferencesOfFile() {
        String fileId = upload();
        service.addRef(fileId, new FileRefCmd(APP, "contract", "c1"));
        service.addRef(fileId, new FileRefCmd(APP, "invoice", "i1"));

        List<FileRefView> views = service.refs(APP, fileId);

        assertThat(views).hasSize(2);
        assertThat(views).extracting(FileRefView::bizId).containsExactlyInAnyOrder("c1", "i1");
    }

    @Test
    void addRefCrossAppFileIsNotFound() {
        String fileId = upload();

        assertThatThrownBy(() -> service.addRef(fileId, new FileRefCmd("app_other", "contract", "c1")))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void addRefWithoutBizIdIsRejected() {
        String fileId = upload();

        assertThatThrownBy(() -> service.addRef(fileId, new FileRefCmd(APP, "contract", " ")))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void removeRefIsIdempotentAndClearsCount() {
        String fileId = upload();
        service.addRef(fileId, new FileRefCmd(APP, "contract", "c1"));

        service.removeRef(fileId, new FileRefCmd(APP, "contract", "c1"));
        service.removeRef(fileId, new FileRefCmd(APP, "contract", "c1"));

        assertThat(refs.countByFile(APP, fileId)).isZero();
        assertThat(service.refs(APP, fileId)).isEmpty();
    }

    @Test
    void refsOfCrossAppFileIsNotFound() {
        String fileId = upload();

        assertThatThrownBy(() -> service.refs("app_other", fileId))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    private String upload() {
        FileUploadResult result = fileService.upload(
                new FileUploadCmd(APP, "a.txt", "text/plain", null, null, null),
                "x".getBytes(StandardCharsets.UTF_8), "u1");
        return result.fileId();
    }
}
