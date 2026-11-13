package com.foundation.org.infrastructure;

import com.foundation.org.domain.JobGrade;

import java.util.List;
import java.util.Optional;

/** 职级仓储（F-03 §7.3，MyBatis-Plus）。 */
public interface JobGradeRepository {

    Optional<JobGrade> findById(String id);

    Optional<JobGrade> findByCode(String code);

    boolean existsByCode(String code);

    List<JobGrade> list();

    void insert(JobGrade jobGrade);

    void update(JobGrade jobGrade);

    void deleteById(String id);
}
