package com.foundation.sync.adapter.ldap;

import com.foundation.sync.spi.SyncCredentialsRef;

import java.util.List;
import java.util.Optional;

/**
 * LDAP 出网 I/O 抽象（F-08 BAS-12）：把真实 JNDI 调用隔离在实现之后，便于无网络单测。
 *
 * <p>分页、限流与退避重试由实现内部完成，调用方只需关心「按 baseDn + filter 取全部匹配项」。
 */
public interface LdapApiClient {

    /** 认证：记录/解析凭证引用（ADR-042），后续调用据此建立连接。 */
    void authenticate(SyncCredentialsRef credentials);

    /** 连通性探测：可建立连接并绑定返回 true。 */
    boolean ping();

    /** 按 baseDn + filter 全量检索（实现内部按 {@code pageSize} 分页）。 */
    List<LdapEntry> entries(String baseDn, String filter);

    /** 单对象回查（DN 精确查询）。 */
    Optional<LdapEntry> entry(String dn);
}
