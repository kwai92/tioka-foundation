package com.foundation.file.domain;

/**
 * 文件服务主键前缀（00-施工总纲 §2.2：{@code {前缀}_{ulid}}，列宽 {@code VARCHAR(32)}）。
 */
public final class FileIdPrefix {

    /** 文件对象：{@code fil_{ulid}}。 */
    public static final String FILE = "fil";

    /** 业务对象引用：{@code frf_{ulid}}。 */
    public static final String FILE_REF = "frf";

    private FileIdPrefix() {
    }
}
