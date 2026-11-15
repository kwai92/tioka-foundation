package com.foundation.authz.api;

import com.foundation.authz.api.DataScopeApi;
import com.foundation.authz.api.PermissionSubjectView;
import com.foundation.authz.api.PersonDimensionCmd;
import com.foundation.authz.api.RoleDataScopeCmd;
import com.foundation.authz.api.RoleDataScopeView;
import com.foundation.authz.application.PermissionSubjectQueryService;
import com.foundation.starter.security.permission.RequirePermission;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/**
 * 数据权限 REST（F-06 BAS-06 §接口 {@code /api/fdn}）：角色数据范围与人员维度授予。
 *
 * <p>只在 Foundation 定义与计算，不执行；执行在应用内由 BAS-08 的 starter 承载。
 *
 * <p>本类不标 {@code @RestController}：由 {@code AuthzAutoConfiguration} 以 {@code @Bean} 装配，
 * 保证与 {@link DataScopeApi} 同生命周期（无持久化上下文时不注册）。
 */
@ResponseBody
@RequestMapping("/api/fdn")
public class DataScopeController {

    private final DataScopeApi dataScopes;
    private final PermissionSubjectQueryService permissionSubjects;

    public DataScopeController(DataScopeApi dataScopes, PermissionSubjectQueryService permissionSubjects) {
        this.dataScopes = dataScopes;
        this.permissionSubjects = permissionSubjects;
    }

    /** 读回角色数据范围。 */
    @GetMapping("/roles/{roleCode}/data-scope")
    @RequirePermission("fdn:datascope:read")
    public RoleDataScopeView roleDataScope(@PathVariable String roleCode) {
        return dataScopes.roleDataScope(roleCode);
    }

    /** 设置角色数据范围（部门档位 + 各维度取值，覆盖式写入）。 */
    @PutMapping("/roles/{roleCode}/data-scope")
    @RequirePermission("fdn:datascope:admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setRoleDataScope(@PathVariable String roleCode, @RequestBody RoleDataScopeCmd cmd) {
        dataScopes.setRoleDataScope(roleCode, cmd);
    }

    /** 人员维度授予，返回新增授权 ID 列表。 */
    @PostMapping("/persons/{personId}/dimensions")
    @RequirePermission("fdn:datascope:admin")
    @ResponseStatus(HttpStatus.CREATED)
    public IdsResponse grantPersonDimension(@PathVariable String personId,
                                            @RequestBody PersonDimensionCmd cmd) {
        return new IdsResponse(dataScopes.grantPersonDimension(personId, cmd));
    }

    /** 撤销人员维度授予（不动角色授予）。 */
    @DeleteMapping("/persons/{personId}/dimensions/{dimension}")
    @RequirePermission("fdn:datascope:admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokePersonDimension(@PathVariable String personId, @PathVariable String dimension) {
        dataScopes.revokePersonDimension(personId, dimension);
    }

    /** 谁有这个权限（无应用参数，跨应用反查；与 {@code GET /api/authz/who} 同一服务）。 */
    @GetMapping("/permissions/{permissionCode}/subjects")
    @RequirePermission("fdn:grant:admin")
    public PermissionSubjectView subjects(@PathVariable String permissionCode) {
        return permissionSubjects.whoHas(permissionCode, null);
    }

    /** 人员维度授权 ID 列表响应体。 */
    public record IdsResponse(List<String> ids) {
    }
}
