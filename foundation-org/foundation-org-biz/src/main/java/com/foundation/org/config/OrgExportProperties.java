package com.foundation.org.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Excel 导出配置（F-03 §4.2、§7.1），前缀 {@code foundation.org.export}。 */
@ConfigurationProperties(prefix = "foundation.org.export")
public class OrgExportProperties {

    /** 单次导出最大行。 */
    private int maxRows = 100000;

    /** 导出是否含手机 / 邮箱明文，默认脱敏。 */
    private boolean includeSensitive = false;

    public int getMaxRows() {
        return maxRows;
    }

    public void setMaxRows(int maxRows) {
        this.maxRows = maxRows;
    }

    public boolean isIncludeSensitive() {
        return includeSensitive;
    }

    public void setIncludeSensitive(boolean includeSensitive) {
        this.includeSensitive = includeSensitive;
    }
}
