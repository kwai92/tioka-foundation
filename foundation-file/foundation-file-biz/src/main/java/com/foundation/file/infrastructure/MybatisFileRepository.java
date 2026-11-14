package com.foundation.file.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.file.domain.DataLabel;
import com.foundation.file.domain.FileStatus;
import com.foundation.file.domain.ScanStatus;
import com.foundation.file.domain.StorageKind;
import com.foundation.file.domain.StoredFile;
import com.foundation.file.infrastructure.mapper.FileMapper;
import com.foundation.file.infrastructure.persistence.FileDO;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/** 文件对象仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisFileRepository implements FileRepository {

    private final FileMapper mapper;

    public MybatisFileRepository(FileMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(StoredFile file, String operator) {
        FileDO row = new FileDO();
        row.setId(file.id());
        row.setAppCode(file.appCode());
        row.setBizType(file.bizType());
        row.setBizId(file.bizId());
        row.setFileName(file.fileName());
        row.setContentType(file.contentType());
        row.setSizeBytes(file.sizeBytes());
        row.setSha256(file.sha256());
        row.setStorageKind(file.storageKind().name());
        row.setStorageKey(file.storageKey());
        row.setDataLabel(file.dataLabel().name());
        row.setScanStatus(file.scanStatus().name());
        row.setStatus(file.status().name());
        row.setUploadedBy(file.uploadedBy());
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        row.setCreateAt(file.createAt() == null ? now : file.createAt());
        row.setUpdateAt(file.updateAt() == null ? row.getCreateAt() : file.updateAt());
        mapper.insert(row);
    }

    @Override
    public Optional<StoredFile> find(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisFileRepository::toDomain);
    }

    @Override
    public void markDeleted(String id, String operator) {
        FileDO row = new FileDO();
        row.setId(id);
        row.setStatus(FileStatus.DELETED.name());
        row.setUpdateAt(LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
        mapper.updateById(row);
    }

    @Override
    public void updateScanStatus(String id, ScanStatus scanStatus, String operator) {
        FileDO row = new FileDO();
        row.setId(id);
        row.setScanStatus(scanStatus.name());
        row.setUpdateAt(LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
        mapper.updateById(row);
    }

    @Override
    public Optional<FileStatus> statusOf(String id) {
        FileDO row = mapper.selectOne(Wrappers.<FileDO>lambdaQuery()
                .select(FileDO::getStatus)
                .eq(FileDO::getId, id)
                .last("limit 1"));
        return row == null ? Optional.empty() : Optional.of(FileStatus.of(row.getStatus()));
    }

    private static StoredFile toDomain(FileDO row) {
        return new StoredFile(row.getId(), row.getAppCode(), row.getBizType(), row.getBizId(),
                row.getFileName(), row.getContentType(), row.getSizeBytes() == null ? 0L : row.getSizeBytes(),
                row.getSha256(), StorageKind.of(row.getStorageKind()), row.getStorageKey(),
                DataLabel.of(row.getDataLabel()), ScanStatus.of(row.getScanStatus()),
                FileStatus.of(row.getStatus()), row.getUploadedBy(), row.getCreateAt(), row.getUpdateAt());
    }
}
