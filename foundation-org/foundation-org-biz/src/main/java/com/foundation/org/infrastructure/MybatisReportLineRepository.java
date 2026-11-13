package com.foundation.org.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.org.domain.ReportLine;
import com.foundation.org.infrastructure.mapper.ReportLineMapper;
import com.foundation.org.infrastructure.persistence.ReportLineDO;

import java.util.List;
import java.util.Optional;

/** 汇报关系仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisReportLineRepository implements ReportLineRepository {

    private final ReportLineMapper mapper;

    public MybatisReportLineRepository(ReportLineMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<ReportLine> findByPersonId(String personId) {
        return Optional.ofNullable(mapper.selectById(personId)).map(MybatisReportLineRepository::toDomain);
    }

    @Override
    public Optional<String> managerOf(String personId) {
        return findByPersonId(personId).map(ReportLine::managerPersonId);
    }

    @Override
    public List<ReportLine> findByManager(String managerPersonId) {
        return mapper.selectList(Wrappers.<ReportLineDO>lambdaQuery()
                        .eq(ReportLineDO::getManagerPersonId, managerPersonId))
                .stream().map(MybatisReportLineRepository::toDomain).toList();
    }

    @Override
    public void upsert(ReportLine reportLine) {
        ReportLineDO existing = mapper.selectById(reportLine.personId());
        if (existing == null) {
            ReportLineDO row = new ReportLineDO();
            row.setPersonId(reportLine.personId());
            row.setManagerPersonId(reportLine.managerPersonId());
            row.setSource(reportLine.source());
            mapper.insert(row);
            return;
        }
        mapper.update(null, Wrappers.<ReportLineDO>lambdaUpdate()
                .set(ReportLineDO::getManagerPersonId, reportLine.managerPersonId())
                .set(ReportLineDO::getSource, reportLine.source())
                .eq(ReportLineDO::getPersonId, reportLine.personId()));
    }

    @Override
    public void deleteByPersonId(String personId) {
        mapper.deleteById(personId);
    }

    private static ReportLine toDomain(ReportLineDO row) {
        return new ReportLine(row.getPersonId(), row.getManagerPersonId(), row.getSource());
    }
}
