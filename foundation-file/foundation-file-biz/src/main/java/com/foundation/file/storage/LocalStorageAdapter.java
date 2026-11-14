package com.foundation.file.storage;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.file.api.UploadedPart;
import com.foundation.file.domain.StorageKind;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 本地磁盘存储后端（ADR-026）：开发与单机部署默认，也是分片合并的自持实现。
 *
 * <p>对象落 {@code {localRoot}/{key}}；分片中间态落 {@code {localRoot}/.multipart/{key}/{partNumber}}，
 * 合并完成后清理该目录。
 */
public class LocalStorageAdapter implements ObjectStorageAdapter {

    private static final String MULTIPART_DIR = ".multipart";

    private final Path root;

    public LocalStorageAdapter(StorageSettings settings) {
        String localRoot = settings.localRoot();
        if (localRoot == null || localRoot.isBlank()) {
            throw new BizException(ErrorCode.INTERNAL, "foundation.file.storage.local-root 未配置");
        }
        this.root = Path.of(localRoot).toAbsolutePath().normalize();
    }

    @Override
    public StorageKind kind() {
        return StorageKind.LOCAL;
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        Path target = objectPath(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content == null ? new byte[0] : content,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            throw new BizException(ErrorCode.INTERNAL, "本地存储写入失败：" + ex.getMessage());
        }
    }

    @Override
    public InputStream openStream(String key) {
        Path target = objectPath(key);
        try {
            return Files.newInputStream(target, StandardOpenOption.READ);
        } catch (IOException ex) {
            throw new BizException(ErrorCode.NOT_FOUND, "对象不存在：" + key);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(objectPath(key));
        } catch (IOException ex) {
            throw new BizException(ErrorCode.INTERNAL, "本地存储删除失败：" + ex.getMessage());
        }
    }

    @Override
    public String initMultipart(String key, String contentType) {
        try {
            Files.createDirectories(partDir(key));
        } catch (IOException ex) {
            throw new BizException(ErrorCode.INTERNAL, "分片目录创建失败：" + ex.getMessage());
        }
        return "";
    }

    @Override
    public Optional<String> presignedUploadPart(String key, String nativeUploadId, int partNumber, Duration ttl) {
        // 本地后端没有可直连的对象存储，分片经 Foundation 自身端点接收。
        return Optional.empty();
    }

    @Override
    public void putPart(String key, int partNumber, byte[] content) {
        Path target = partDir(key).resolve(String.valueOf(partNumber));
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content == null ? new byte[0] : content,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            throw new BizException(ErrorCode.INTERNAL, "分片写入失败：" + ex.getMessage());
        }
    }

    @Override
    public void completeMultipart(String key, String nativeUploadId, List<UploadedPart> parts) {
        Path target = objectPath(key);
        Path tempFile;
        try {
            Files.createDirectories(target.getParent());
            tempFile = Files.createTempFile(target.getParent(), ".merge-", ".tmp");
            try (OutputStream out = Files.newOutputStream(tempFile, StandardOpenOption.WRITE)) {
                for (UploadedPart part : parts) {
                    Path partFile = partDir(key).resolve(String.valueOf(part.partNumber()));
                    if (!Files.exists(partFile)) {
                        throw new BizException(ErrorCode.BAD_REQUEST, "缺少分片：" + part.partNumber());
                    }
                    Files.copy(partFile, out);
                }
            }
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BizException(ErrorCode.INTERNAL, "分片合并失败：" + ex.getMessage());
        } finally {
            cleanParts(key);
        }
    }

    @Override
    public void abortMultipart(String key, String nativeUploadId) {
        cleanParts(key);
    }

    @Override
    public Optional<String> presignedDownload(String key, Duration ttl, String fileName, String contentType) {
        // 本地后端由 SignedUrlService 颁发 Foundation 自身的短时地址。
        return Optional.empty();
    }

    private Path objectPath(String key) {
        return resolveSafely(root, key);
    }

    private Path partDir(String key) {
        return objectPath(MULTIPART_DIR + "/" + key);
    }

    private void cleanParts(String key) {
        Path dir = partDir(key);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 延迟清理：残留中间态不影响业务
                }
            });
        } catch (IOException ignored) {
            // 同上
        }
    }

    /** 防目录穿越：对象键必须落在根目录之内。 */
    private static Path resolveSafely(Path root, String key) {
        if (key == null || key.isBlank()) {
            throw new BizException(ErrorCode.INTERNAL, "对象键不能为空");
        }
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法对象键");
        }
        return resolved;
    }
}
