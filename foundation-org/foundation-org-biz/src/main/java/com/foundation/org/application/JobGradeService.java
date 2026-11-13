package com.foundation.org.application;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.id.Ids;
import com.foundation.org.api.dto.ChangeAction;
import com.foundation.org.api.dto.ChangeEntityType;
import com.foundation.org.api.dto.ChangeSource;
import com.foundation.org.api.dto.JobGradeCmd;
import com.foundation.org.api.dto.JobGradeView;
import com.foundation.org.domain.JobGrade;
import com.foundation.org.domain.OrgIdPrefix;
import com.foundation.org.infrastructure.JobGradeRepository;
import com.foundation.org.infrastructure.OrgChangeLogWriter;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 职级应用服务（F-03 §5、§7.3）：增删改查，写变更历史。 */
public class JobGradeService {

    private final JobGradeRepository jobGrades;
    private final OrgChangeLogWriter changeLog;

    public JobGradeService(JobGradeRepository jobGrades, OrgChangeLogWriter changeLog) {
        this.jobGrades = jobGrades;
        this.changeLog = changeLog;
    }

    @Transactional
    public JobGradeView create(JobGradeCmd cmd) {
        requireText(cmd.code(), "职级编码必填");
        requireText(cmd.name(), "职级名称必填");
        requireText(cmd.series(), "职级序列必填");
        if (jobGrades.existsByCode(cmd.code())) {
            throw new BizException(ErrorCode.CONFLICT, "职级编码已存在：" + cmd.code());
        }
        JobGrade jobGrade = new JobGrade(Ids.newId(OrgIdPrefix.JOB_GRADE), cmd.code(), cmd.name(), cmd.series(),
                cmd.gradeLevel() == null ? 0 : cmd.gradeLevel(), cmd.sortNo() == null ? 0 : cmd.sortNo());
        jobGrades.insert(jobGrade);
        changeLog.append(ChangeEntityType.JOB_GRADE, jobGrade.id(), ChangeAction.CREATE, null, summary(jobGrade),
                ChangeSource.MANUAL, null);
        return jobGrade.toView();
    }

    @Transactional
    public JobGradeView update(String id, JobGradeCmd cmd) {
        JobGrade current = requireJobGrade(id);
        String code = cmd.code() == null ? current.code() : cmd.code();
        if (!code.equals(current.code()) && jobGrades.existsByCode(code)) {
            throw new BizException(ErrorCode.CONFLICT, "职级编码已存在：" + code);
        }
        JobGrade updated = new JobGrade(id, code, cmd.name() == null ? current.name() : cmd.name(),
                cmd.series() == null ? current.series() : cmd.series(),
                cmd.gradeLevel() == null ? current.gradeLevel() : cmd.gradeLevel(),
                cmd.sortNo() == null ? current.sortNo() : cmd.sortNo());
        jobGrades.update(updated);
        changeLog.append(ChangeEntityType.JOB_GRADE, id, ChangeAction.UPDATE, summary(current), summary(updated),
                ChangeSource.MANUAL, null);
        return updated.toView();
    }

    @Transactional
    public void delete(String id) {
        JobGrade current = requireJobGrade(id);
        jobGrades.deleteById(id);
        changeLog.append(ChangeEntityType.JOB_GRADE, id, ChangeAction.DISABLE, summary(current), null,
                ChangeSource.MANUAL, null);
    }

    public List<JobGradeView> list() {
        return jobGrades.list().stream().map(JobGrade::toView).toList();
    }

    private JobGrade requireJobGrade(String id) {
        return jobGrades.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "职级不存在：" + id));
    }

    private static Map<String, Object> summary(JobGrade jobGrade) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", jobGrade.code());
        map.put("name", jobGrade.name());
        map.put("series", jobGrade.series());
        map.put("gradeLevel", jobGrade.gradeLevel());
        return map;
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, message);
        }
    }
}
