package com.foundation.org.infrastructure;

import com.foundation.org.domain.ReportLine;

import java.util.List;
import java.util.Optional;

/** 汇报关系仓储（F-03 §7.3，MyBatis-Plus）。 */
public interface ReportLineRepository {

    Optional<ReportLine> findByPersonId(String personId);

    /** 人员的直属上级 ID。 */
    Optional<String> managerOf(String personId);

    /** 直属上级为某人的汇报关系。 */
    List<ReportLine> findByManager(String managerPersonId);

    void upsert(ReportLine reportLine);

    void deleteByPersonId(String personId);
}
