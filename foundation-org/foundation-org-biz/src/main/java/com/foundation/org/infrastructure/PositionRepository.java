package com.foundation.org.infrastructure;

import com.foundation.org.domain.Position;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 岗位仓储（F-03 §7.3，MyBatis-Plus）。 */
public interface PositionRepository {

    Optional<Position> findById(String id);

    Optional<Position> findByCode(String code);

    Optional<Position> findBySourceExternalId(String source, String externalId);

    boolean existsByCode(String code);

    List<Position> list(int page, int size);

    long count();

    List<Position> findByOrgUnit(String orgUnitId);

    /** 一组组织单元下的岗位计数（组织树展示用）。 */
    Map<String, Long> countByOrgUnit(Collection<String> orgUnitIds);

    void insert(Position position);

    void update(Position position);

    void deleteById(String id);
}
