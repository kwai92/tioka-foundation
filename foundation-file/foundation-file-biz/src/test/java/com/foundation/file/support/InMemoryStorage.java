package com.foundation.file.support;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.file.api.UploadedPart;
import com.foundation.file.domain.StorageKind;
import com.foundation.file.storage.ObjectStorageAdapter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** 测试用内存对象存储（F-09 BAS-17）：验证应用服务逻辑，不依赖文件系统或容器。 */
public final class InMemoryStorage implements ObjectStorageAdapter {

    private final StorageKind kind;
    private final boolean presigned;
    private final Map<String, byte[]> objects = new LinkedHashMap<>();
    private final Map<String, Map<Integer, byte[]>> parts = new LinkedHashMap<>();

    public InMemoryStorage(StorageKind kind, boolean presigned) {
        this.kind = kind;
        this.presigned = presigned;
    }

    @Override
    public StorageKind kind() {
        return kind;
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        objects.put(key, content == null ? new byte[0] : content);
    }

    @Override
    public InputStream openStream(String key) {
        byte[] content = objects.get(key);
        if (content == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "对象不存在：" + key);
        }
        return new ByteArrayInputStream(content);
    }

    @Override
    public void delete(String key) {
        objects.remove(key);
    }

    @Override
    public String initMultipart(String key, String contentType) {
        parts.put(key, new TreeMap<>());
        return presigned ? "native-1" : "";
    }

    @Override
    public Optional<String> presignedUploadPart(String key, String nativeUploadId, int partNumber, Duration ttl) {
        return presigned ? Optional.of("https://s3.test/" + key + "?partNumber=" + partNumber) : Optional.empty();
    }

    @Override
    public void putPart(String key, int partNumber, byte[] content) {
        parts.computeIfAbsent(key, ignored -> new TreeMap<>()).put(partNumber, content);
    }

    @Override
    public void completeMultipart(String key, String nativeUploadId, List<UploadedPart> uploaded) {
        Map<Integer, byte[]> staged = parts.getOrDefault(key, Map.of());
        ByteArrayOutputStream merged = new ByteArrayOutputStream();
        for (UploadedPart part : uploaded) {
            byte[] content = staged.get(part.partNumber());
            if (content == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "缺少分片：" + part.partNumber());
            }
            merged.writeBytes(content);
        }
        objects.put(key, merged.toByteArray());
        parts.remove(key);
    }

    @Override
    public void abortMultipart(String key, String nativeUploadId) {
        parts.remove(key);
    }

    @Override
    public Optional<String> presignedDownload(String key, Duration ttl, String fileName, String contentType) {
        return presigned ? Optional.of("https://s3.test/" + key) : Optional.empty();
    }

    /** 已落对象的字节内容；不存在返回 {@code null}。 */
    public byte[] object(String key) {
        return objects.get(key);
    }
}
