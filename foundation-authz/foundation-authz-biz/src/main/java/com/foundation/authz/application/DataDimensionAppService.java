package com.foundation.authz.application;

import com.foundation.authz.api.DataDimensionApi;
import com.foundation.authz.api.DataDimensionCmd;
import com.foundation.authz.api.DataDimensionUpdateCmd;
import com.foundation.authz.api.DataDimensionView;
import com.foundation.authz.api.DimensionValueSyncCmd;
import com.foundation.authz.api.DimensionValueSyncResult;
import com.foundation.authz.domain.AuthzIdPrefix;
import com.foundation.authz.domain.DataDimension;
import com.foundation.authz.domain.ValueSource;
import com.foundation.authz.infrastructure.DataDimensionRepository;
import com.foundation.authz.infrastructure.DimensionValueRepository;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.id.Ids;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 数据维度维护（F-06 BAS-06 §接口 {@code /api/fdn/dimensions}）。
 *
 * <p>维度可由清单声明或管理台新建；{@code appCode} 为空表示全局维度；{@code code} 创建后不可改。
 */
public class DataDimensionAppService implements DataDimensionApi {

    private final DataDimensionRepository dimensions;
    private final DimensionValueRepository values;
    private final DimensionValueSyncService syncService;

    public DataDimensionAppService(DataDimensionRepository dimensions, DimensionValueRepository values,
                                   DimensionValueSyncService syncService) {
        this.dimensions = dimensions;
        this.values = values;
        this.syncService = syncService;
    }

    @Override
    public List<DataDimensionView> list(String appCode) {
        return dimensions.list(appCode).stream().map(DataDimensionAppService::toView).toList();
    }

    @Override
    @Transactional
    public String create(DataDimensionCmd cmd) {
        String appCode = normalize(cmd.appCode());
        dimensions.findByCode(appCode, cmd.code()).ifPresent(existing -> {
            throw new BizException(ErrorCode.CONFLICT, "维度编码已存在：" + cmd.code());
        });
        String id = Ids.newId(AuthzIdPrefix.DATA_DIMENSION);
        dimensions.insert(new DataDimension(id, appCode, cmd.code(), cmd.name(),
                ValueSource.of(cmd.valueSource()), cmd.hierarchical()));
        return id;
    }

    @Override
    @Transactional
    public void update(String appCode, String code, DataDimensionUpdateCmd cmd) {
        DataDimension dimension = dimensions.findByCode(normalize(appCode), code)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "维度不存在：" + code));
        ValueSource valueSource = ValueSource.of(cmd.valueSource());
        if (valueSource != dimension.valueSource()) {
            values.deleteByDimension(dimension.id());
        }
        dimensions.update(new DataDimension(dimension.id(), dimension.appCode(), dimension.code(),
                cmd.name(), valueSource, cmd.hierarchical()));
    }

    @Override
    @Transactional
    public DimensionValueSyncResult syncValues(String appCode, String code, DimensionValueSyncCmd cmd) {
        DataDimension dimension = dimensions.findByCode(normalize(appCode), code)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "维度不存在：" + code));
        return syncService.sync(dimension.id(), cmd);
    }

    private static String normalize(String appCode) {
        return appCode == null || appCode.isBlank() ? null : appCode;
    }

    private static DataDimensionView toView(DataDimension dimension) {
        return new DataDimensionView(dimension.id(), dimension.appCode(), dimension.code(),
                dimension.name(), dimension.valueSource().name(), dimension.hierarchical());
    }
}
