package com.foundation.file.support;

import com.foundation.file.domain.FileStatus;
import com.foundation.file.domain.ScanStatus;
import com.foundation.file.domain.StoredFile;
import com.foundation.file.infrastructure.FileRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** 测试用内存文件仓储（F-09 BAS-17）。 */
public final class InMemoryFileRepository implements FileRepository {

    private final Map<String, StoredFile> rows = new LinkedHashMap<>();

    @Override
    public void insert(StoredFile file, String operator) {
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        rows.put(file.id(), new StoredFile(file.id(), file.appCode(), file.bizType(), file.bizId(),
                file.fileName(), file.contentType(), file.sizeBytes(), file.sha256(), file.storageKind(),
                file.storageKey(), file.dataLabel(), file.scanStatus(), file.status(), file.uploadedBy(),
                file.createAt() == null ? now : file.createAt(),
                file.updateAt() == null ? now : file.updateAt()));
    }

    @Override
    public Optional<StoredFile> find(String id) {
        return Optional.ofNullable(rows.get(id));
    }

    @Override
    public void markDeleted(String id, String operator) {
        rows.computeIfPresent(id, (key, file) -> copy(file, file.scanStatus(), FileStatus.DELETED));
    }

    @Override
    public void updateScanStatus(String id, ScanStatus scanStatus, String operator) {
        rows.computeIfPresent(id, (key, file) -> copy(file, scanStatus, file.status()));
    }

    @Override
    public Optional<FileStatus> statusOf(String id) {
        return find(id).map(StoredFile::status);
    }

    private static StoredFile copy(StoredFile file, ScanStatus scanStatus, FileStatus status) {
        return new StoredFile(file.id(), file.appCode(), file.bizType(), file.bizId(), file.fileName(),
                file.contentType(), file.sizeBytes(), file.sha256(), file.storageKind(), file.storageKey(),
                file.dataLabel(), scanStatus, status, file.uploadedBy(), file.createAt(), file.updateAt());
    }
}
