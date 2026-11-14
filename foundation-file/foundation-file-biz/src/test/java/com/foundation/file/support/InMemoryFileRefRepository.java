package com.foundation.file.support;

import com.foundation.file.domain.FileRef;
import com.foundation.file.infrastructure.FileRefRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 测试用内存文件引用仓储（F-09 BAS-17）；按「文件 + 应用 + 业务对象」唯一。 */
public final class InMemoryFileRefRepository implements FileRefRepository {

    private final Map<String, FileRef> rows = new LinkedHashMap<>();

    @Override
    public Optional<FileRef> find(String fileId, String appCode, String bizType, String bizId) {
        return Optional.ofNullable(rows.get(key(fileId, appCode, bizType, bizId)));
    }

    @Override
    public void insert(FileRef ref) {
        rows.putIfAbsent(key(ref.fileId(), ref.appCode(), ref.bizType(), ref.bizId()), ref);
    }

    @Override
    public void delete(String fileId, String appCode, String bizType, String bizId) {
        rows.remove(key(fileId, appCode, bizType, bizId));
    }

    @Override
    public List<FileRef> listByFile(String appCode, String fileId) {
        List<FileRef> result = new ArrayList<>();
        for (FileRef ref : rows.values()) {
            if (ref.appCode().equals(appCode) && ref.fileId().equals(fileId)) {
                result.add(ref);
            }
        }
        return result;
    }

    @Override
    public long countByFile(String appCode, String fileId) {
        return listByFile(appCode, fileId).size();
    }

    private static String key(String fileId, String appCode, String bizType, String bizId) {
        return fileId + "|" + appCode + "|" + bizType + "|" + bizId;
    }
}
