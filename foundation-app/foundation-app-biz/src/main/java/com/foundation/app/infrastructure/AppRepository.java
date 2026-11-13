package com.foundation.app.infrastructure;

import com.foundation.app.domain.App;

import java.util.List;
import java.util.Optional;

/** 应用仓储（F-05 BAS-04 §迁移 V3 {@code fdn_app}）。 */
public interface AppRepository {

    List<App> list();

    Optional<App> find(String code);

    Optional<App> findById(String id);

    void insert(App app, String operator);

    void update(App app, String operator);

    /** 推进权限清单版本（BAS-05 清单导入成功后调用；版本只能前进）。 */
    void updateManifestVersion(String id, long version, String operator);
}
