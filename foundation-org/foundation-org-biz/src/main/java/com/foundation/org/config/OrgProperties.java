package com.foundation.org.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 组织域基础配置（F-03 §7.1）。
 *
 * <p>前缀 {@code foundation.org}。{@code import} / {@code export} / {@code person} 因 {@code import}
 * 是 Java 保留字无法作属性名，拆为带各自前缀的配置类（{@link OrgImportProperties}、
 * {@link OrgExportProperties}、{@link OrgPersonProperties}），映射关系不变。
 */
@ConfigurationProperties(prefix = "foundation.org")
public class OrgProperties {

    /** 组织树最大深度，超限拒绝创建与移动。 */
    private int maxTreeDepth = 10;

    public int getMaxTreeDepth() {
        return maxTreeDepth;
    }

    public void setMaxTreeDepth(int maxTreeDepth) {
        this.maxTreeDepth = maxTreeDepth;
    }
}
