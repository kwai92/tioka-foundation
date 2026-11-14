package com.foundation.sync.adapter.ldap;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * LDAP / AD 同步适配器配置（F-08 BAS-12）。
 *
 * <p>仅描述连接与拉取参数；凭证明文经 {@code credential_ref}（ADR-042）解析，本类中的
 * {@link #bindPassword} 仅为占位，正式实现须改由密钥来源注入。
 */
@ConfigurationProperties(prefix = "foundation.sync.adapter.ldap")
public class LdapProperties {

    /** LDAP 服务地址，如 {@code ldap://host:389}。 */
    private String url = "";

    /** 绑定 DN（只读账号）。 */
    private String bindDn = "";

    /** 绑定口令的密钥引用（ADR-042），如 {@code secret://foundation/ldap-bind}。 */
    private String bindPasswordRef = "";

    /** 绑定口令占位值；正式应经 {@link #bindPasswordRef} 解析，勿在配置中存放明文。 */
    private String bindPassword = "";

    /** 搜索起始 DN，可配置多个。 */
    private List<String> baseDns = new ArrayList<>();

    /** 人员过滤：默认 {@code (objectClass=person)}。 */
    private String userFilter = "(objectClass=person)";

    /** 组织单元过滤：默认 {@code (objectClass=organizationalUnit)}。 */
    private String orgFilter = "(objectClass=organizationalUnit)";

    /** 分页大小。 */
    private int pageSize = 500;

    /** 限流 QPS。 */
    private int qps = 10;

    /** 失败重试次数。 */
    private int maxRetries = 3;

    /** 退避基准毫秒（指数退避）。 */
    private long backoffMs = 500;

    /** 组织树最大深度，超过则截断。 */
    private int maxTreeDepth = 10;

    /** 增量方式：AUTO / CHANGE_NUMBER / TIMESTAMP。 */
    private String incrementalMode = "AUTO";

    /** 是否 AD 模式：true 用 uSNChanged/whenChanged，false（OpenLDAP）用 modifyTimestamp。 */
    private boolean adMode = false;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getBindDn() {
        return bindDn;
    }

    public void setBindDn(String bindDn) {
        this.bindDn = bindDn;
    }

    public String getBindPasswordRef() {
        return bindPasswordRef;
    }

    public void setBindPasswordRef(String bindPasswordRef) {
        this.bindPasswordRef = bindPasswordRef;
    }

    public String getBindPassword() {
        return bindPassword;
    }

    public void setBindPassword(String bindPassword) {
        this.bindPassword = bindPassword;
    }

    public List<String> getBaseDns() {
        return baseDns;
    }

    public void setBaseDns(List<String> baseDns) {
        this.baseDns = baseDns == null ? new ArrayList<>() : baseDns;
    }

    public String getUserFilter() {
        return userFilter;
    }

    public void setUserFilter(String userFilter) {
        this.userFilter = userFilter;
    }

    public String getOrgFilter() {
        return orgFilter;
    }

    public void setOrgFilter(String orgFilter) {
        this.orgFilter = orgFilter;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getQps() {
        return qps;
    }

    public void setQps(int qps) {
        this.qps = qps;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getBackoffMs() {
        return backoffMs;
    }

    public void setBackoffMs(long backoffMs) {
        this.backoffMs = backoffMs;
    }

    public int getMaxTreeDepth() {
        return maxTreeDepth;
    }

    public void setMaxTreeDepth(int maxTreeDepth) {
        this.maxTreeDepth = maxTreeDepth;
    }

    public String getIncrementalMode() {
        return incrementalMode;
    }

    public void setIncrementalMode(String incrementalMode) {
        this.incrementalMode = incrementalMode;
    }

    public boolean isAdMode() {
        return adMode;
    }

    public void setAdMode(boolean adMode) {
        this.adMode = adMode;
    }
}
