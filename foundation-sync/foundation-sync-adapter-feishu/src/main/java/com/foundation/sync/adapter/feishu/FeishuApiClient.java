package com.foundation.sync.adapter.feishu;

import com.foundation.sync.spi.Page;
import com.foundation.sync.spi.PageCursor;
import com.foundation.sync.spi.SyncCredentialsRef;

import java.util.Optional;

/**
 * 飞书通讯录出网 I/O 抽象（F-08 BAS-12）。
 *
 * <p>把真实 HTTP 调用与 {@link FeishuSyncAdapter} 解耦，便于无网络单测：
 * 生产实现为 {@link HttpFeishuApiClient}，测试可用内存桩。
 */
public interface FeishuApiClient {

    /** 认证：记录凭证引用并按需换取访问令牌。 */
    void authenticate(SyncCredentialsRef credentials);

    /** 连通性探测：可换取令牌即视为可达。 */
    boolean ping();

    /** 分页拉取部门。 */
    Page<FeishuDepartment> departments(PageCursor cursor);

    /** 分页拉取用户。 */
    Page<FeishuUser> users(PageCursor cursor);

    /** 按 open_id 回查单个用户。 */
    Optional<FeishuUser> user(String openId);

    /** 按 open_department_id 回查单个部门。 */
    Optional<FeishuDepartment> department(String openDepartmentId);
}
