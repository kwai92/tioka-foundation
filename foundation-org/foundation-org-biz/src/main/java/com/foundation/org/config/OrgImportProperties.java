package com.foundation.org.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Excel 导入配置（F-03 §4.1、§7.1），前缀 {@code foundation.org.import}。 */
@ConfigurationProperties(prefix = "foundation.org.import")
public class OrgImportProperties {

    /** 导入模板版本。 */
    private String templateVersion = "v1";

    /** 单事务分批写入行数。 */
    private int batchSize = 200;

    /** 单文件最大数据行。 */
    private int maxRows = 20000;

    public String getTemplateVersion() {
        return templateVersion;
    }

    public void setTemplateVersion(String templateVersion) {
        this.templateVersion = templateVersion;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxRows() {
        return maxRows;
    }

    public void setMaxRows(int maxRows) {
        this.maxRows = maxRows;
    }
}
