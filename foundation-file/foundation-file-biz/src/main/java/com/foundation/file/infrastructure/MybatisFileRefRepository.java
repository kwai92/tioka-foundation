package com.foundation.file.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.file.domain.FileRef;
import com.foundation.file.infrastructure.mapper.FileRefMapper;
import com.foundation.file.infrastructure.persistence.FileRefDO;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 文件引用仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisFileRefRepository implements FileRefRepository {

    private final FileRefMapper mapper;

    public MybatisFileRefRepository(FileRefMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<FileRef> find(String fileId, String appCode, String bizType, String bizId) {
        return mapper.selectList(Wrappers.<FileRefDO>lambdaQuery()
                        .eq(FileRefDO::getFileId, fileId)
                        .eq(FileRefDO::getAppCode, appCode)
                        .eq(FileRefDO::getBizType, bizType)
                        .eq(FileRefDO::getBizId, bizId)
                        .last("limit 1"))
                .stream().findFirst().map(MybatisFileRefRepository::toDomain);
    }

    @Override
    public void insert(FileRef ref) {
        FileRefDO row = new FileRefDO();
        row.setId(ref.id());
        row.setFileId(ref.fileId());
        row.setAppCode(ref.appCode());
        row.setBizType(ref.bizType());
        row.setBizId(ref.bizId());
        row.setCreateAt(ref.createAt() == null
                ? LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)
                : ref.createAt());
        mapper.insert(row);
    }

    @Override
    public void delete(String fileId, String appCode, String bizType, String bizId) {
        mapper.delete(Wrappers.<FileRefDO>lambdaQuery()
                .eq(FileRefDO::getFileId, fileId)
                .eq(FileRefDO::getAppCode, appCode)
                .eq(FileRefDO::getBizType, bizType)
                .eq(FileRefDO::getBizId, bizId));
    }

    @Override
    public List<FileRef> listByFile(String appCode, String fileId) {
        return mapper.selectList(Wrappers.<FileRefDO>lambdaQuery()
                        .eq(FileRefDO::getAppCode, appCode)
                        .eq(FileRefDO::getFileId, fileId)
                        .orderByAsc(FileRefDO::getCreateAt))
                .stream().map(MybatisFileRefRepository::toDomain).toList();
    }

    @Override
    public long countByFile(String appCode, String fileId) {
        return mapper.selectCount(Wrappers.<FileRefDO>lambdaQuery()
                .eq(FileRefDO::getAppCode, appCode)
                .eq(FileRefDO::getFileId, fileId));
    }

    private static FileRef toDomain(FileRefDO row) {
        return new FileRef(row.getId(), row.getFileId(), row.getAppCode(), row.getBizType(),
                row.getBizId(), row.getCreateAt());
    }
}
