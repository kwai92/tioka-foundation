package com.foundation.sync.adapter.ldap;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.sync.spi.SyncCredentialsRef;

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
 * <p>凭证：{@link #authenticate(SyncCredentialsRef)} 记录引用；真实明文应经 ADR-042 的密钥来源解析，
 * 当前实现以 {@link LdapProperties#getBindPassword()} 占位（见 TODO）。
 */
public class JndiLdapClient implements LdapApiClient {

    private static final String LDAP_CTX_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";
    private static final String CONNECT_TIMEOUT = "com.sun.jndi.ldap.connect.timeout";
    private static final String READ_TIMEOUT = "com.sun.jndi.ldap.read.timeout";
    private static final String CONNECT_POOL = "com.sun.jndi.ldap.connect.pool";
    private static final int ACCOUNTDISABLE = 0x0002;
    private static final DateTimeFormatter LDAP_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'").withZone(ZoneOffset.UTC);

    private final LdapProperties properties;

    private volatile SyncCredentialsRef credentials;

    public JndiLdapClient(LdapProperties properties) {
        this.properties = properties;
    }

    @Override
    public void authenticate(SyncCredentialsRef credentials) {
        this.credentials = credentials;
        // TODO(ADR-042): 经 credential_ref 解析绑定口令；当前以 properties.getBindPassword() 占位。
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

    private Hashtable<String, Object> environment() {
        Hashtable<String, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, LDAP_CTX_FACTORY);
        env.put(Context.PROVIDER_URL, properties.getUrl());
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        if (properties.getBindDn() != null && !properties.getBindDn().isBlank()) {
            env.put(Context.SECURITY_PRINCIPAL, properties.getBindDn());
        }
        String password = properties.getBindPassword();
        if (password != null && !password.isEmpty()) {
            env.put(Context.SECURITY_CREDENTIALS, password);
        } else if (credentials != null && credentials.secretRef() != null) {
            env.put(Context.SECURITY_CREDENTIALS, credentials.secretRef());
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
