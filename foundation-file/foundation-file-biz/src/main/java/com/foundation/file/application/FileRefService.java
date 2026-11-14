package com.foundation.file.application;

import com.foundation.common.id.Ids;
import com.foundation.file.api.FileRefApi;
import com.foundation.file.api.FileRefCmd;
import com.foundation.file.api.FileRefView;
import com.foundation.file.domain.FileIdPrefix;
import com.foundation.file.domain.FileRef;
import com.foundation.file.domain.FileRules;
import com.foundation.file.infrastructure.FileRefRepository;
import com.foundation.file.infrastructure.FileRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 文件引用服务（F-09 BAS-17）：业务对象与文件的引用关系，是删除保护与归档的判据。
 *
 * <p>同一四元组（{@code fileId, appCode, bizType, bizId}）幂等；引用方必须能看到该文件
 * （跨应用 {@code NOT_FOUND}）。
 */
public class FileRefService implements FileRefApi {

    private final FileRepository files;
    private final FileRefRepository refs;

    public FileRefService(FileRepository files, FileRefRepository refs) {
        this.files = files;
        this.refs = refs;
    }

    @Override
    public FileRefView addRef(String fileId, FileRefCmd cmd) {
        String appCode = FileRules.requireAppCode(cmd.appCode());
        FileLookup.require(files, appCode, fileId);
        FileLookup.requireBiz(cmd.bizType(), cmd.bizId());
        return refs.find(fileId, appCode, cmd.bizType(), cmd.bizId())
                .map(FileRefService::toView)
                .orElseGet(() -> insert(fileId, appCode, cmd));
    }

    @Override
    public void removeRef(String fileId, FileRefCmd cmd) {
        String appCode = FileRules.requireAppCode(cmd.appCode());
        FileLookup.require(files, appCode, fileId);
        refs.delete(fileId, appCode, cmd.bizType(), cmd.bizId());
    }

    @Override
    public List<FileRefView> refs(String appCode, String fileId) {
        String app = FileRules.requireAppCode(appCode);
        FileLookup.require(files, app, fileId);
        return refs.listByFile(app, fileId).stream().map(FileRefService::toView).toList();
    }

    private FileRefView insert(String fileId, String appCode, FileRefCmd cmd) {
        FileRef ref = new FileRef(Ids.newId(FileIdPrefix.FILE_REF), fileId, appCode, cmd.bizType(),
                cmd.bizId(), LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
        refs.insert(ref);
        return toView(ref);
    }

    private static FileRefView toView(FileRef ref) {
        return new FileRefView(ref.id(), ref.fileId(), ref.appCode(), ref.bizType(), ref.bizId(), ref.createAt());
    }
}
