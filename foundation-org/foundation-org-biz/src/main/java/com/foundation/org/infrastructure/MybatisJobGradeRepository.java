package com.foundation.org.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.org.domain.JobGrade;
import com.foundation.org.infrastructure.mapper.JobGradeMapper;
import com.foundation.org.infrastructure.persistence.JobGradeDO;

import java.util.List;
import java.util.Optional;

/** 职级仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisJobGradeRepository implements JobGradeRepository {

    private final JobGradeMapper mapper;

    public MybatisJobGradeRepository(JobGradeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<JobGrade> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisJobGradeRepository::toDomain);
    }

    @Override
    public Optional<JobGrade> findByCode(String code) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<JobGradeDO>lambdaQuery().eq(JobGradeDO::getCode, code))).map(MybatisJobGradeRepository::toDomain);
    }

    @Override
    public boolean existsByCode(String code) {
        return mapper.selectCount(Wrappers.<JobGradeDO>lambdaQuery().eq(JobGradeDO::getCode, code)) > 0;
    }

    @Override
    public List<JobGrade> list() {
        return mapper.selectList(Wrappers.<JobGradeDO>lambdaQuery()
                        .orderByAsc(JobGradeDO::getSeries).orderByAsc(JobGradeDO::getGradeLevel))
                .stream().map(MybatisJobGradeRepository::toDomain).toList();
    }

    @Override
    public void insert(JobGrade jobGrade) {
        mapper.insert(toDO(jobGrade));
    }

    @Override
    public void update(JobGrade jobGrade) {
        mapper.update(null, Wrappers.<JobGradeDO>lambdaUpdate()
                .set(JobGradeDO::getCode, jobGrade.code())
                .set(JobGradeDO::getName, jobGrade.name())
                .set(JobGradeDO::getSeries, jobGrade.series())
                .set(JobGradeDO::getGradeLevel, jobGrade.gradeLevel())
                .set(JobGradeDO::getSortNo, jobGrade.sortNo())
                .eq(JobGradeDO::getId, jobGrade.id()));
    }

    @Override
    public void deleteById(String id) {
        mapper.deleteById(id);
    }

    private static JobGradeDO toDO(JobGrade jobGrade) {
        JobGradeDO row = new JobGradeDO();
        row.setId(jobGrade.id());
        row.setCode(jobGrade.code());
        row.setName(jobGrade.name());
        row.setSeries(jobGrade.series());
        row.setGradeLevel(jobGrade.gradeLevel());
        row.setSortNo(jobGrade.sortNo());
        return row;
    }

    private static JobGrade toDomain(JobGradeDO row) {
        return new JobGrade(row.getId(), row.getCode(), row.getName(), row.getSeries(),
                row.getGradeLevel() == null ? 0 : row.getGradeLevel(),
                row.getSortNo() == null ? 0 : row.getSortNo());
    }
}
