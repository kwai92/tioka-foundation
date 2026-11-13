package com.foundation.org.api;

import com.foundation.org.api.dto.JobGradeCmd;
import com.foundation.org.api.dto.JobGradeView;
import com.foundation.org.application.JobGradeService;
import com.foundation.starter.security.permission.RequirePermission;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 职级 REST（F-03 §5）。 */
@RestController
@RequestMapping("/api/fdn/job-grades")
@RequirePermission("org:write")
public class JobGradeController {

    private final JobGradeService jobGrades;

    public JobGradeController(JobGradeService jobGrades) {
        this.jobGrades = jobGrades;
    }

    @GetMapping
    public List<JobGradeView> list() {
        return jobGrades.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobGradeView create(@RequestBody JobGradeCmd cmd) {
        return jobGrades.create(cmd);
    }

    @PutMapping("/{id}")
    public JobGradeView update(@PathVariable String id, @RequestBody JobGradeCmd cmd) {
        return jobGrades.update(id, cmd);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        jobGrades.delete(id);
    }
}
