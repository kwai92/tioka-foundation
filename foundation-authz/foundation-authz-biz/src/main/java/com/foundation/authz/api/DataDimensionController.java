package com.foundation.authz.api;

import com.foundation.authz.api.DataDimensionApi;
import com.foundation.authz.api.DataDimensionCmd;
import com.foundation.authz.api.DataDimensionUpdateCmd;
import com.foundation.authz.api.DataDimensionView;
import com.foundation.authz.api.DimensionValueSyncCmd;
import com.foundation.authz.api.DimensionValueSyncResult;
import com.foundation.starter.security.permission.RequirePermission;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/**
 * 数据维度 REST（F-06 BAS-06 §接口 {@code /api/fdn/dimensions}）。
 *
 * <p>维度可由清单声明（BAS-05）或管理台新建；{@code app} 为空表示全局维度；{@code code} 创建后不可改。
 *
 * <p>本类不标 {@code @RestController}：由 {@code AuthzAutoConfiguration} 以 {@code @Bean} 装配，
 * 保证与 {@link DataDimensionApi} 同生命周期（无持久化上下文时不注册）。
 */
@ResponseBody
@RequestMapping("/api/fdn/dimensions")
public class DataDimensionController {

    private final DataDimensionApi dimensions;

    public DataDimensionController(DataDimensionApi dimensions) {
        this.dimensions = dimensions;
    }

    /** 维度列表。 */
    @GetMapping
    @RequirePermission("fdn:dimension:read")
    public List<DataDimensionView> list(@RequestParam(required = false) String app) {
        return dimensions.list(app);
    }

    /** 新建维度，返回维度 ID。 */
    @PostMapping
    @RequirePermission("fdn:dimension:admin")
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse create(@RequestBody DataDimensionCmd cmd) {
        return new IdResponse(dimensions.create(cmd));
    }

    /** 修改维度名称、是否层级与值来源（编码不可改）。 */
    @PutMapping("/{code}")
    @RequirePermission("fdn:dimension:admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable String code, @RequestParam(required = false) String app,
                       @RequestBody DataDimensionUpdateCmd cmd) {
        dimensions.update(app, code, cmd);
    }

    /** 应用上报维度值增量维护（幂等）。 */
    @PutMapping("/{code}/values:sync")
    @RequirePermission("fdn:dimension:sync")
    public DimensionValueSyncResult syncValues(@PathVariable String code,
                                               @RequestParam(required = false) String app,
                                               @RequestBody DimensionValueSyncCmd cmd) {
        return dimensions.syncValues(app, code, cmd);
    }

    /** 维度 ID 响应体。 */
    public record IdResponse(String id) {
    }
}
