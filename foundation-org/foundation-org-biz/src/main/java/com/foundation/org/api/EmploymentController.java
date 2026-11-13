package com.foundation.org.api;

import com.foundation.org.api.dto.EmploymentCmd;
import com.foundation.org.api.dto.EmploymentView;
import com.foundation.org.application.EmploymentService;
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

/** 任职 REST（F-03 §5 {@code /api/fdn/persons/{id}/employments}）。 */
@RestController
@RequestMapping("/api/fdn/persons/{personId}/employments")
@RequirePermission("org:person:write")
public class EmploymentController {

    private final EmploymentService employments;

    public EmploymentController(EmploymentService employments) {
        this.employments = employments;
    }

    @GetMapping
    public List<EmploymentView> list(@PathVariable String personId) {
        return employments.list(personId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmploymentView create(@PathVariable String personId, @RequestBody EmploymentCmd cmd) {
        return employments.create(personId, cmd);
    }

    @PutMapping("/{employmentId}")
    public EmploymentView update(@PathVariable String personId, @PathVariable String employmentId,
                                 @RequestBody EmploymentCmd cmd) {
        return employments.update(personId, employmentId, cmd);
    }

    @DeleteMapping("/{employmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String personId, @PathVariable String employmentId) {
        employments.delete(personId, employmentId);
    }
}
