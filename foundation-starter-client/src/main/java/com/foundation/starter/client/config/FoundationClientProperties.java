package com.foundation.starter.client.config;

import com.foundation.starter.core.runtime.FoundationRuntimeMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 中台客户端配置（F-07 §13：{@code foundation.client.*} / {@code dict.*} / {@code param.*} / {@code org.*} / {@code person.*}）。 */
@ConfigurationProperties(prefix = "foundation")
public class FoundationClientProperties {

    /** 出站与重试（{@code foundation.client.*}）。 */
    private final Client client = new Client();

    /** 字典缓存（{@code foundation.dict.*}）。 */
    private final Cache dict = new Cache(Duration.ofMinutes(10));

    /** 参数缓存（{@code foundation.param.*}）。 */
    private final Cache param = new Cache(Duration.ofMinutes(10));

    /** 组织缓存（{@code foundation.org.*}）。 */
    private final Cache org = new Cache(Duration.ofMinutes(10));

    /** 人员缓存（{@code foundation.person.*}）。 */
    private final Cache person = new Cache(Duration.ofMinutes(10));

    /** 应用间调用（{@code foundation.app-grant.*}，占位，实现见 F-12）。 */
    private final AppGrant appGrant = new AppGrant();

    public Client getClient() {
        return client;
    }

    public Cache getDict() {
        return dict;
    }

    public Cache getParam() {
        return param;
    }

    public Cache getOrg() {
        return org;
    }

    public Cache getPerson() {
        return person;
    }

    public AppGrant getAppGrant() {
        return appGrant;
    }

    /** 出站与重试配置。 */
    public static class Client {

        private Duration connectTimeout = Duration.ofSeconds(1);
        private Duration readTimeout = Duration.ofSeconds(3);

        /** 读接口重试上限（指数退避 + 抖动）。 */
        private final Retry retry = new Retry();

        /** 熔断（短窗口失败率）。 */
        private final CircuitBreaker circuitBreaker = new CircuitBreaker();

        /** 只读能力降级（{@code foundation.client.degrade.*}，ADP-08）。 */
        private final Degrade degrade = new Degrade();

        /** 接入运行模式（{@code foundation.client.mode}，ADP-08）；缺省 FOUNDATION 保持零改动。 */
        private FoundationRuntimeMode mode = FoundationRuntimeMode.FOUNDATION;

        /** 本地种子数据（{@code foundation.client.local.*}，ADP-08）。 */
        private final Local local = new Local();

        /**
         * 授权快照版本化缓存（P1，{@code foundation.client.authz.*}）。
         * 刻意放在 client starter 自己的命名空间：{@code foundation.authz.*} 已由
         * {@code foundation-starter-security} 的 Snapshot / Event 配置占用。
         */
        private final Authz authz = new Authz();

        public Authz getAuthz() {
            return authz;
        }

        /**
         * 授权快照缓存配置（P1）。
         *
         * <p>命中未过期快照时不再回源，中台退出请求热路径；{@code fdn.authz.changed}
         * 事件失效后下次取用回源到新版本。TTL 只是事件丢失的兜底，<b>不是降级许可</b>：
         * 条目过期且回源失败一律拒绝，不存在「过期放行」开关（36 号规划 §6）。
         * {@code cache-ttl=0s} 可退回每次回源，用于排障或对账。
         */
        public static class Authz {

            private Duration cacheTtl = Duration.ofMinutes(5);

            public Duration getCacheTtl() {
                return cacheTtl;
            }

            public void setCacheTtl(Duration cacheTtl) {
                this.cacheTtl = cacheTtl == null || cacheTtl.isNegative()
                        ? Duration.ZERO : cacheTtl;
            }
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }

        public Retry getRetry() {
            return retry;
        }

        public CircuitBreaker getCircuitBreaker() {
            return circuitBreaker;
        }

        public Degrade getDegrade() {
            return degrade;
        }

        public FoundationRuntimeMode getMode() {
            return mode;
        }

        public void setMode(FoundationRuntimeMode mode) {
            this.mode = mode == null ? FoundationRuntimeMode.FOUNDATION : mode;
        }

        public Local getLocal() {
            return local;
        }
    }

    /**
     * 本地种子数据（{@code foundation.client.local.*}）。
     *
     * <p>只在 {@code foundation.client.mode=local|self-managed} 且
     * {@code foundation.client.local.enabled=true} 时生效：让 D 类轻量服务与
     * 「Foundation 停机时的回退部署」在不连中台、不引中间件的情况下仍能读到
     * 字典、参数、组织与人员。<b>刻意不默认开启</b>——底座不替业务方编造主数据，
     * 接入方也可以按 {@code @ConditionalOnMissingBean} 自行注册本地实现。
     *
     * <p>敏感参数只写不读（F-02 BAS-09）：列在 {@code secret-params} 中的键一律返回空。
     */
    public static class Local {

        private boolean enabled = false;

        /** 字典种子：{@code 类型编码 -> 字典项列表}。 */
        private Map<String, List<DictSeed>> dict = new LinkedHashMap<>();

        /** 参数种子：{@code 参数键 -> 值}。 */
        private Map<String, String> param = new LinkedHashMap<>();

        /** 视为敏感、拒绝读取的参数键。 */
        private List<String> secretParams = new ArrayList<>();

        private List<OrgUnitSeed> orgUnits = new ArrayList<>();

        private List<PersonSeed> persons = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Map<String, List<DictSeed>> getDict() {
            return dict;
        }

        public void setDict(Map<String, List<DictSeed>> dict) {
            this.dict = dict == null ? new LinkedHashMap<>() : dict;
        }

        public Map<String, String> getParam() {
            return param;
        }

        public void setParam(Map<String, String> param) {
            this.param = param == null ? new LinkedHashMap<>() : param;
        }

        public List<String> getSecretParams() {
            return secretParams;
        }

        public void setSecretParams(List<String> secretParams) {
            this.secretParams = secretParams == null ? new ArrayList<>() : secretParams;
        }

        public List<OrgUnitSeed> getOrgUnits() {
            return orgUnits;
        }

        public void setOrgUnits(List<OrgUnitSeed> orgUnits) {
            this.orgUnits = orgUnits == null ? new ArrayList<>() : orgUnits;
        }

        public List<PersonSeed> getPersons() {
            return persons;
        }

        public void setPersons(List<PersonSeed> persons) {
            this.persons = persons == null ? new ArrayList<>() : persons;
        }
    }

    /** 本地字典项种子。 */
    public static class DictSeed {

        private String itemCode;
        private String itemName;
        private int sort;
        private String appCode;

        public String getItemCode() {
            return itemCode;
        }

        public void setItemCode(String itemCode) {
            this.itemCode = itemCode;
        }

        public String getItemName() {
            return itemName;
        }

        public void setItemName(String itemName) {
            this.itemName = itemName;
        }

        public int getSort() {
            return sort;
        }

        public void setSort(int sort) {
            this.sort = sort;
        }

        public String getAppCode() {
            return appCode;
        }

        public void setAppCode(String appCode) {
            this.appCode = appCode;
        }
    }

    /** 本地组织单元种子。 */
    public static class OrgUnitSeed {

        private String id;
        private String code;
        private String name;
        private String parentId;
        private int sort;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getParentId() {
            return parentId;
        }

        public void setParentId(String parentId) {
            this.parentId = parentId;
        }

        public int getSort() {
            return sort;
        }

        public void setSort(int sort) {
            this.sort = sort;
        }
    }

    /** 本地人员种子。 */
    public static class PersonSeed {

        private String id;
        private String employeeNo;
        private String name;
        private String accountId;
        private String primaryOrgUnit;
        private String status = "ACTIVE";

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getEmployeeNo() {
            return employeeNo;
        }

        public void setEmployeeNo(String employeeNo) {
            this.employeeNo = employeeNo;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getAccountId() {
            return accountId;
        }

        public void setAccountId(String accountId) {
            this.accountId = accountId;
        }

        public String getPrimaryOrgUnit() {
            return primaryOrgUnit;
        }

        public void setPrimaryOrgUnit(String primaryOrgUnit) {
            this.primaryOrgUnit = primaryOrgUnit;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    /**
     * 降级策略配置（{@code foundation.client.degrade.*}）。
     *
     * <p>全局默认 {@code fail-closed}；只有显式改为 {@code stale-cache} 且在
     * {@code capabilities} 白名单内的只读能力才会使用过期缓存。身份与授权类不在白名单内，
     * 无论如何配置都保持拒绝（36 号规划 §6「降级语义」、待决策 D4 的保守默认值）。
     */
    public static class Degrade {

        private DegradePolicy policy = DegradePolicy.FAIL_CLOSED;

        /** 过期缓存可容忍的最大陈旧时长，超过即不再兜底。 */
        private Duration maxStale = Duration.ofMinutes(30);

        /** 允许使用过期缓存的只读能力白名单。 */
        private java.util.List<String> capabilities = new java.util.ArrayList<>(
                java.util.List.of("dict", "param", "org", "person"));

        public DegradePolicy getPolicy() {
            return policy;
        }

        public void setPolicy(DegradePolicy policy) {
            this.policy = policy == null ? DegradePolicy.FAIL_CLOSED : policy;
        }

        public Duration getMaxStale() {
            return maxStale;
        }

        public void setMaxStale(Duration maxStale) {
            this.maxStale = maxStale == null ? Duration.ofMinutes(30) : maxStale;
        }

        public java.util.List<String> getCapabilities() {
            return capabilities;
        }

        public void setCapabilities(java.util.List<String> capabilities) {
            this.capabilities = capabilities == null ? java.util.List.of() : capabilities;
        }

        /** 按能力求实际生效的策略；白名单外一律 FAIL_CLOSED。 */
        public DegradePolicy policyFor(String capability) {
            return policy == DegradePolicy.STALE_CACHE && capabilities.contains(capability)
                    ? DegradePolicy.STALE_CACHE
                    : DegradePolicy.FAIL_CLOSED;
        }

        public long maxStaleMillis() {
            return maxStale.toMillis();
        }
    }

    /** 重试配置。 */
    public static class Retry {

        private int maxAttempts = 3;
        private Duration baseDelay = Duration.ofMillis(100);

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getBaseDelay() {
            return baseDelay;
        }

        public void setBaseDelay(Duration baseDelay) {
            this.baseDelay = baseDelay;
        }
    }

    /** 熔断配置。 */
    public static class CircuitBreaker {

        /** 失败率阈值（百分比），如 {@code 50}。 */
        private int failureRate = 50;

        /** 短窗口内最小样本数，低于该数不熔断。 */
        private int minCalls = 20;

        /** 统计窗口。 */
        private Duration window = Duration.ofSeconds(60);

        /** 熔断后开启时长，期满进入半开。 */
        private Duration openDuration = Duration.ofSeconds(30);

        public int getFailureRate() {
            return failureRate;
        }

        public void setFailureRate(int failureRate) {
            this.failureRate = failureRate;
        }

        public int getMinCalls() {
            return minCalls;
        }

        public void setMinCalls(int minCalls) {
            this.minCalls = minCalls;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }

        public Duration getOpenDuration() {
            return openDuration;
        }

        public void setOpenDuration(Duration openDuration) {
            this.openDuration = openDuration;
        }
    }

    /** 缓存配置（字典 / 参数 / 组织 / 人员同构：TTL 兜底 + 事件刷新）。 */
    public static class Cache {

        private Duration cacheTtl = Duration.ofMinutes(10);

        public Cache() {
        }

        public Cache(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
        }

        public Duration getCacheTtl() {
            return cacheTtl;
        }

        public void setCacheTtl(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
        }
    }

    /** 应用间调用配置（占位）。 */
    public static class AppGrant {

        /** 授权关系缓存 TTL（事件丢失兜底）。 */
        private Duration cacheTtl = Duration.ofMinutes(5);

        public Duration getCacheTtl() {
            return cacheTtl;
        }

        public void setCacheTtl(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
        }
    }
}
