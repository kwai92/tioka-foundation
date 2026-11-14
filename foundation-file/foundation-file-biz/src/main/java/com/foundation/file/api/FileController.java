package com.foundation.file.api;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.file.application.MultipartUploadService;
import com.foundation.file.application.SignedUrlService;
import com.foundation.file.storage.UrlSigner;
import com.foundation.starter.audit.annotation.Audited;
import com.foundation.starter.security.permission.PublicApi;
import com.foundation.starter.security.permission.RequirePermission;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文件 REST（F-09 BAS-17 §接口 {@code /api/fdn/files}）：上传、元数据、下载、预签名、引用与删除。
 *
 * <p>{@code appCode} 一律取自当前登录身份（{@link Principal#appCode()}），**不接受调用方传入**，
 * 这是按应用隔离的执行点。
 *
 * <p>本类不标 {@code @RestController}：由 {@code FileAutoConfiguration} 以 {@code @Bean} 装配。
 */
@ResponseBody
@RequestMapping("/api/fdn/files")
public class FileController {

    private static final String UPLOAD = "fdn:file:upload";

    private static final String READ = "fdn:file:read";

    private final FileApi files;
    private final FileRefApi refs;
    private final MultipartUploadService multipart;
    private final SignedUrlService signedUrls;

    public FileController(FileApi files, FileRefApi refs, MultipartUploadService multipart,
                          SignedUrlService signedUrls) {
        this.files = files;
        this.refs = refs;
        this.multipart = multipart;
        this.signedUrls = signedUrls;
    }

    /** 小文件直传（{@code multipart/form-data}）。 */
    @PostMapping
    @RequirePermission(UPLOAD)
    @Audited(action = "fdn.file.upload", resource = "fdn_file")
    public FileUploadResult upload(@RequestParam("file") MultipartFile file,
                                   @RequestParam(required = false) String contentType,
                                   @RequestParam(required = false) String dataLabel,
                                   @RequestParam(required = false) String bizType,
                                   @RequestParam(required = false) String bizId) throws IOException {
        String resolved = contentType == null || contentType.isBlank() ? file.getContentType() : contentType;
        FileUploadCmd cmd = new FileUploadCmd(appCode(), file.getOriginalFilename(), resolved,
                dataLabel, bizType, bizId);
        return files.upload(cmd, file.getBytes(), operator());
    }

    /** 分片上传初始化。 */
    @PostMapping("/multipart:init")
    @RequirePermission(UPLOAD)
    public MultipartInitView multipartInit(@RequestBody MultipartInitRequest request) {
        return files.multipartInit(new MultipartInitCmd(appCode(), request.fileName(), request.sizeBytes(),
                request.contentType(), request.dataLabel(), request.bizType(), request.bizId()));
    }

    /** 接收一个分片（仅本地存储后端使用；对象存储后端的分片由预签名 URL 直传）。 */
    @PutMapping("/multipart:part")
    @RequirePermission(UPLOAD)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void multipartPart(@RequestParam String uploadId, @RequestParam int partNumber,
                              @RequestBody byte[] content) {
        multipart.putPart(appCode(), uploadId, partNumber, content);
    }

    /** 分片合并并校验 {@code sha256}。 */
    @PostMapping("/multipart:complete")
    @RequirePermission(UPLOAD)
    @Audited(action = "fdn.file.upload", resource = "fdn_file")
    public FileUploadResult multipartComplete(@RequestBody MultipartCompleteRequest request) {
        return files.multipartComplete(new MultipartCompleteCmd(appCode(), request.uploadId(), request.fileName(),
                request.sizeBytes(), request.contentType(), request.sha256(), request.dataLabel(),
                request.bizType(), request.bizId(), request.parts()));
    }

    /** 元数据。 */
    @GetMapping("/{id}")
    @RequirePermission(READ)
    public FileView metadata(@PathVariable String id) {
        return files.metadata(appCode(), id);
    }

    /** 短时预签名下载地址（TTL 见 {@code foundation.file.signed-url-ttl}）。 */
    @GetMapping("/{id}/signed-url")
    @RequirePermission(READ)
    public SignedUrlView signedUrl(@PathVariable String id,
                                   @RequestParam(required = false) String disposition) {
        return files.signedUrl(appCode(), id, disposition);
    }

    /** 流式下载。 */
    @GetMapping("/{id}/content")
    @RequirePermission(READ)
    public ResponseEntity<StreamingResponseBody> content(@PathVariable String id) {
        return stream(files.download(appCode(), id));
    }

    /** 凭短时令牌下载（本地存储后端；令牌由「预签名 URL」颁发，无需登录）。 */
    @GetMapping("/signed/{token}")
    @PublicApi
    public ResponseEntity<StreamingResponseBody> signedDownload(@PathVariable String token) {
        UrlSigner.SignedRef ref = signedUrls.verify(token)
                .orElseThrow(() -> new BizException(ErrorCode.FORBIDDEN, "下载链接无效或已过期"));
        return stream(files.download(ref.appCode(), ref.fileId()));
    }

    /** 建立业务对象对文件的引用（同一四元组幂等）。 */
    @PostMapping("/{id}/refs")
    @RequirePermission(UPLOAD)
    public FileRefView addRef(@PathVariable String id, @RequestBody RefRequest request) {
        return refs.addRef(id, new FileRefCmd(appCode(), request.bizType(), request.bizId()));
    }

    /** 解除引用（不存在时幂等）。 */
    @DeleteMapping("/{id}/refs")
    @RequirePermission(UPLOAD)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeRef(@PathVariable String id, @RequestBody RefRequest request) {
        refs.removeRef(id, new FileRefCmd(appCode(), request.bizType(), request.bizId()));
    }

    /** 删除：有引用时 {@code CONFLICT(40900)}，否则逻辑删除。 */
    @DeleteMapping("/{id}")
    @RequirePermission(UPLOAD)
    @Audited(action = "fdn.file.delete", resource = "fdn_file")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        files.delete(appCode(), id);
    }

    private static ResponseEntity<StreamingResponseBody> stream(FileContent content) {
        StreamingResponseBody body = output -> {
            try (InputStream in = content.stream()) {
                in.transferTo(output);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, content.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition("attachment", content.fileName()))
                .header("X-Data-Label", content.dataLabel())
                .contentLength(content.sizeBytes())
                .body(body);
    }

    private static String contentDisposition(String disposition, String fileName) {
        String type = disposition == null || disposition.isBlank() ? "attachment" : disposition;
        String encoded = URLEncoder.encode(fileName == null || fileName.isBlank() ? "file" : fileName,
                StandardCharsets.UTF_8).replace("+", "%20");
        return type + "; filename*=UTF-8''" + encoded;
    }

    private static String appCode() {
        Principal principal = PrincipalHolder.require();
        String code = principal.appCode();
        if (code == null || code.isBlank()) {
            throw new BizException(ErrorCode.FORBIDDEN, "当前身份缺少应用编码");
        }
        return code;
    }

    private static String operator() {
        return PrincipalHolder.current().map(Principal::userId).orElse("system");
    }

    /** 分片初始化请求体。 */
    public record MultipartInitRequest(String fileName,
                                       long sizeBytes,
                                       String contentType,
                                       String dataLabel,
                                       String bizType,
                                       String bizId) {
    }

    /** 分片合并请求体。 */
    public record MultipartCompleteRequest(String uploadId,
                                           String fileName,
                                           long sizeBytes,
                                           String contentType,
                                           String sha256,
                                           String dataLabel,
                                           String bizType,
                                           String bizId,
                                           List<UploadedPart> parts) {
    }

    /** 引用请求体。 */
    public record RefRequest(String bizType, String bizId) {
    }
}
