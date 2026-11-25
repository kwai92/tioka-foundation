package com.foundation.sync.adapter.ldap;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.sync.spi.CredentialResolver;
import com.foundation.sync.spi.SyncCredentialsRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.PagedResultsControl;
import javax.naming.ldap.PagedResultsResponseControl;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 基于 JDK JNDI 的 {@link LdapApiClient} 实现（F-08 BAS-12）。
 *
 * <p>使用连接池（{@code com.sun.jndi.ldap.connect.pool}）与分页结果控件
 * （{@link PagedResultsControl}）循环拉取；绑定失败/超时按 {@code maxRetries}/{@code backoffMs}
 * 指数退避重试，最终失败包成 {@link BizException}（{@link ErrorCode#INTERNAL}）。
 *
 * <p>凭证（ADR-042）：{@link #authenticate(SyncCredentialsRef)} 按 {@code credential_ref}
 * 经 {@link CredentialResolver} 解析绑定口令；未装载解析器或未配置引用时回落
 * {@link LdapProperties#getBindPassword()} 明文并告警。解析失败即抛 {@link BizException}，不回落。
 */
public class JndiLdapClient implements LdapApiClient {

    private static final Logger log = LoggerFactory.getLogger(JndiLdapClient.class);

    private static final String LDAP_CTX_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";
    private static final String CONNECT_TIMEOUT = "com.sun.jndi.ldap.connect.timeout";
    private static final String READ_TIMEOUT = "com.sun.jndi.ldap.read.timeout";
    private static final String CONNECT_POOL = "com.sun.jndi.ldap.connect.pool";
    private static final int ACCOUNTDISABLE = 0x0002;
    private static final DateTimeFormatter LDAP_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'").withZone(ZoneOffset.UTC);

    private final LdapProperties properties;
    /** 凭证解析器（ADR-042）；{@code null} 表示运行环境未装载，此时只能回落明文配置。 */
    private final CredentialResolver credentialResolver;

    /** {@link #authenticate} 解析出的绑定口令；未解析时为 {@code null}。 */
    private volatile String resolvedBindPassword;

    /** 兼容构造器：未注入解析器时只能使用明文 {@code bindPassword} 配置（本地联调用）。 */
    public JndiLdapClient(LdapProperties properties) {
        this(properties, null);
    }

    public JndiLdapClient(LdapProperties properties, CredentialResolver resolver) {
        this.properties = properties;
        this.credentialResolver = resolver;
    }

    @Override
    public void authenticate(SyncCredentialsRef credentials) {
        this.resolvedBindPassword = resolveBindPassword(credentials);
    }

    /**
     * 解析绑定口令（ADR-042）：有 {@code credential_ref} 且装载了解析器 → 取密文明文；
     * 否则回落 {@code properties.bindPassword} 并告警。
     *
     * <p><b>解析失败不回落明文</b>：格式非法或密钥不存在直接抛 {@link BizException}，让认证阶段
     * 立即失败。
     *
     * <p><b>同时修掉一个既有缺陷</b>：改造前本方法缺失，{@link #environment()} 在明文口令为空时
     * 直接把 {@code credentials.secretRef()}（形如 {@code secret://foundation/ldap-bind}）
     * 当作密码塞进 {@code SECURITY_CREDENTIALS}——即**把引用字符串当口令用**，
     * 必然绑定失败且报错信息看不出原因。现由本方法解析，{@code environment()} 只取解析结果。
     */
    private String resolveBindPassword(SyncCredentialsRef ref) {
        String secretRef = ref == null ? null : ref.secretRef();
        boolean hasRef = secretRef != null && !secretRef.isBlank();
        if (hasRef && credentialResolver != null && credentialResolver.supports(secretRef)) {
            return credentialResolver.resolve(secretRef);
        }
        String fallback = properties.getBindPassword();
        if (hasRef && credentialResolver == null) {
            log.warn("LDAP 凭证引用已配置（{}）但未装载 CredentialResolver，"
                    + "回落明文 bindPassword 配置；生产环境请确认 foundation-sync-biz 已装配", secretRef);
        } else if (!hasRef) {
            log.warn("LDAP 未配置 credential_ref（fdn_sync_source.credential_ref），"
                    + "使用明文 foundation.sync.adapter.ldap.bind-password；"
                    + "明文凭证仅限本地联调，生产必须改为 secret:// 引用（ADR-042）");
        }
        return fallback;
    }

    /**
     * 当前生效的绑定口令：{@link #authenticate} 已解析则用解析值（含引用解析或回落明文），
     * 尚未认证时退回明文配置，保证「未走 authenticate 的直接调用」不回归。
     */
    private String bindPassword() {
        String resolved = resolvedBindPassword;
        return resolved != null ? resolved : properties.getBindPassword();
    }

    @Override
    public boolean ping() {
        LdapContext ctx = null;
        try {
            ctx = createContext();
            return true;
        } catch (BizException e) {
            return false;
        } finally {
            closeQuietly(ctx);
        }
    }

    @Override
    public List<LdapEntry> entries(String baseDn, String filter) {
        List<LdapEntry> result = new ArrayList<>();
        String searchBase = baseDn == null ? "" : baseDn;
        String searchFilter = (filter == null || filter.isBlank()) ? "(objectClass=*)" : filter;
        int pageSize = Math.max(1, properties.getPageSize());

        LdapContext ctx = null;
        try {
            ctx = createContext();
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            controls.setReturningAttributes(null);

            byte[] cookie = null;
            do {
                Control pageControl = (cookie == null || cookie.length == 0)
                        ? new PagedResultsControl(pageSize, Control.CRITICAL)
                        : new PagedResultsControl(pageSize, cookie, Control.CRITICAL);
                ctx.setRequestControls(new Control[]{pageControl});
                NamingEnumeration<SearchResult> results = null;
                try {
                    results = ctx.search(searchBase, searchFilter, controls);
                    while (results.hasMore()) {
                        result.add(toEntry(results.next()));
                    }
                } finally {
                    closeQuietly(results);
                }
                cookie = cookieOf(ctx.getResponseControls());
            } while (cookie != null && cookie.length > 0);
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL, "LDAP 分页检索失败: " + e.getMessage());
        } catch (NamingException e) {
            throw new BizException(ErrorCode.INTERNAL, "LDAP 检索失败: " + e.getMessage());
        } finally {
            closeQuietly(ctx);
        }
        return result;
    }

    @Override
    public Optional<LdapEntry> entry(String dn) {
        LdapContext ctx = null;
        try {
            ctx = createContext();
            Attributes attributes = ctx.getAttributes(dn);
            SearchResult result = new SearchResult(dn, null, attributes);
            return Optional.of(toEntry(result));
        } catch (NameNotFoundException e) {
            return Optional.empty();
        } catch (NamingException e) {
            throw new BizException(ErrorCode.INTERNAL, "LDAP 单对象查询失败: " + e.getMessage());
        } finally {
            closeQuietly(ctx);
        }
    }

    // ---------- 连接与重试 ----------

    private LdapContext createContext() {
        int attempts = Math.max(1, properties.getMaxRetries());
        long backoff = Math.max(0L, properties.getBackoffMs());
        NamingException last = null;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                return new InitialLdapContext(environment(), null);
            } catch (NamingException e) {
                last = e;
                if (attempt < attempts - 1) {
                    sleepQuietly(backoff * (1L << attempt));
                }
            }
        }
        throw new BizException(ErrorCode.INTERNAL, "LDAP 绑定失败: "
                + (last == null ? "unknown" : last.getMessage()));
    }

    /**
     * 组装 JNDI 环境。包级可见以便离线断言凭证装配结果（纯函数，不建连接）。
     *
     * <p><b>安全不变式</b>：{@link Context#SECURITY_CREDENTIALS} 只接受**解析后的明文口令**，
     * 绝不接受 {@code secret://…} 引用原文——改造前此处有 {@code else if} 分支会把引用字符串
     * 当口令塞入，必然绑定失败且报错看不出原因。由 {@code JndiLdapClientTest} 锁定该不变式。
     */
    Hashtable<String, Object> environment() {
        Hashtable<String, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, LDAP_CTX_FACTORY);
        env.put(Context.PROVIDER_URL, properties.getUrl());
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        if (properties.getBindDn() != null && !properties.getBindDn().isBlank()) {
            env.put(Context.SECURITY_PRINCIPAL, properties.getBindDn());
        }
        String password = bindPassword();
        if (password != null && !password.isEmpty()) {
            env.put(Context.SECURITY_CREDENTIALS, password);
        }
        env.put(CONNECT_TIMEOUT, "5000");
        env.put(READ_TIMEOUT, "5000");
        env.put(CONNECT_POOL, "true");
        return env;
    }

    // ---------- 结果映射 ----------

    private LdapEntry toEntry(SearchResult result) throws NamingException {
        Attributes attributes = result.getAttributes();
        Map<String, Object> map = new LinkedHashMap<>();
        if (attributes != null) {
            NamingEnumeration<? extends Attribute> all = attributes.getAll();
            while (all.hasMore()) {
                Attribute attribute = all.next();
                if (attribute.size() > 0) {
                    map.put(attribute.getID(), attribute.get());
                }
            }
        }
        String dn = dnOf(result);
        String externalId = externalIdOf(attributes, dn);
        String changeNumber = firstString(attributes, "uSNChanged");
        Instant changedAt = ldapTime(firstString(attributes, "whenChanged"),
                firstString(attributes, "modifyTimestamp"));
        boolean disabled = isDisabled(attributes);
        return new LdapEntry(dn, map, externalId, changeNumber, changedAt, disabled);
    }

    private static String dnOf(SearchResult result) {
        try {
            return result.getNameInNamespace();
        } catch (UnsupportedOperationException | IllegalStateException e) {
            return result.getName();
        }
    }

    private String externalIdOf(Attributes attributes, String dn) throws NamingException {
        Object guid = attributeValue(attributes, "objectGUID");
        if (guid instanceof byte[] bytes && bytes.length > 0) {
            return ObjectGuidCodec.normalize(bytes);
        }
        String entryUuid = entryUuidValue(attributes);
        if (entryUuid != null) {
            return entryUuid;
        }
        return dn;
    }

    private static String entryUuidValue(Attributes attributes) throws NamingException {
        Object value = attributeValue(attributes, "entryUUID");
        return value == null ? null : ObjectGuidCodec.entryUuid(String.valueOf(value));
    }

    private static boolean isDisabled(Attributes attributes) throws NamingException {
        Object value = attributeValue(attributes, "userAccountControl");
        if (value == null) {
            return false;
        }
        try {
            int uac = Integer.parseInt(String.valueOf(value).trim());
            return (uac & ACCOUNTDISABLE) != 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String firstString(Attributes attributes, String id) throws NamingException {
        Object value = attributeValue(attributes, id);
        if (value == null) {
            return null;
        }
        String s = value instanceof String str ? str : String.valueOf(value);
        return s.isBlank() ? null : s.trim();
    }

    private static Object attributeValue(Attributes attributes, String id) throws NamingException {
        if (attributes == null) {
            return null;
        }
        Attribute attribute = attributes.get(id);
        if (attribute == null || attribute.size() == 0) {
            return null;
        }
        return attribute.get();
    }

    private static Instant ldapTime(String... rawValues) {
        for (String raw : rawValues) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String s = raw.trim();
            int dot = s.indexOf('.');
            if (dot >= 0) {
                int z = s.indexOf('Z', dot);
                s = s.substring(0, dot) + (z >= 0 ? "Z" : "");
            }
            try {
                return Instant.from(LDAP_TIME.parse(s));
            } catch (RuntimeException ignored) {
                // 继续尝试其它格式
            }
            try {
                return Instant.parse(raw);
            } catch (RuntimeException ignored) {
                // 尝试下一个取值
            }
        }
        return null;
    }

    private static byte[] cookieOf(Control[] controls) throws IOException {
        if (controls == null) {
            return null;
        }
        for (Control control : controls) {
            if (control instanceof PagedResultsResponseControl response) {
                return response.getCookie();
            }
        }
        return null;
    }

    private static void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeQuietly(LdapContext ctx) {
        if (ctx == null) {
            return;
        }
        try {
            ctx.close();
        } catch (NamingException ignored) {
            // 关闭失败无需上抛
        }
    }

    private static void closeQuietly(NamingEnumeration<?> results) {
        if (results == null) {
            return;
        }
        try {
            results.close();
        } catch (NamingException ignored) {
            // 关闭失败无需上抛
        }
    }
}
