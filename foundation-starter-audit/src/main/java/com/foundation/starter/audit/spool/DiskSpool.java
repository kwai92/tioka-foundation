package com.foundation.starter.audit.spool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.starter.audit.report.AuditEvent;
import com.foundation.common.id.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 审计本地磁盘兜底（F-07 §6）：投递重试耗尽后落盘，后台补偿线程扫描重放。
 *
 * <p>**磁盘 spool 是文件，不建表**（F-07 头部「迁移编号段」）。写入用「临时文件 + 原子改名」，
 * 避免补偿线程读到半截文件。
 */
public class DiskSpool {

    private static final Logger log = LoggerFactory.getLogger(DiskSpool.class);
    private static final String SUFFIX = ".json";

    private final Path directory;
    private final ObjectMapper objectMapper;

    public DiskSpool(Path directory, ObjectMapper objectMapper) {
        this.directory = directory;
        this.objectMapper = objectMapper;
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw new UncheckedIOException("创建审计 spool 目录失败：" + directory, ex);
        }
    }

    /** 落盘一条审计。 */
    public Path spool(AuditEvent event) {
        Path target = directory.resolve("audit-" + Ids.newId("sp").substring(3) + SUFFIX);
        Path temp = directory.resolve(target.getFileName() + ".tmp");
        try {
            objectMapper.writeValue(temp.toFile(), event);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return target;
        } catch (IOException ex) {
            throw new UncheckedIOException("审计落盘失败：" + target, ex);
        }
    }

    /** 批量落盘；单条失败不影响其余（返回成功条数）。 */
    public int spoolAll(Collection<AuditEvent> events) {
        int spooled = 0;
        for (AuditEvent event : events) {
            try {
                spool(event);
                spooled++;
            } catch (RuntimeException ex) {
                log.warn("审计落盘失败，已跳过：{}", event.eventId(), ex);
            }
        }
        return spooled;
    }

    /** 待重放的落盘文件（按文件名即时间序）。 */
    public List<Path> pending() {
        try (Stream<Path> stream = Files.list(directory)) {
            List<Path> files = new ArrayList<>();
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(SUFFIX))
                    .forEach(files::add);
            files.sort(Comparator.comparing(path -> path.getFileName().toString()));
            return files;
        } catch (IOException ex) {
            throw new UncheckedIOException("扫描审计 spool 目录失败：" + directory, ex);
        }
    }

    /** 读取落盘的一条审计。 */
    public AuditEvent read(Path path) {
        try {
            return objectMapper.readValue(path.toFile(), AuditEvent.class);
        } catch (IOException ex) {
            throw new UncheckedIOException("读取审计 spool 文件失败：" + path, ex);
        }
    }

    /** 删除已重放的文件。 */
    public void remove(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            throw new UncheckedIOException("删除审计 spool 文件失败：" + path, ex);
        }
    }

    /** 当前待重放条数。 */
    public int size() {
        return pending().size();
    }
}
