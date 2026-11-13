package com.foundation.org.api;

import com.foundation.org.api.dto.DigitalEmployeeCmd;
import com.foundation.org.api.dto.OrgChangeView;
import com.foundation.org.api.dto.PageResult;
import com.foundation.org.api.dto.PersonCreateCmd;
import com.foundation.org.api.dto.PersonStatus;
import com.foundation.org.api.dto.PersonType;
import com.foundation.org.api.dto.PersonUpdateCmd;
import com.foundation.org.api.dto.PersonView;
import com.foundation.org.api.dto.ReportLineCmd;
import com.foundation.org.application.PersonService;
import com.foundation.starter.security.permission.RequirePermission;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;
import com.foundation.starter.security.snapshot.AuthzSnapshot;
import com.foundation.starter.security.snapshot.AuthzSnapshotClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 人员 REST（F-03 §5）：列表一律脱敏；详情按权限决定明文 / 脱敏（具备 {@code org:person:write}
 * 的调用方看到明文，其余脱敏）。
 */
@RestController
@RequestMapping("/api/fdn/persons")
public class PersonController {

    /** 详情返回明文所需权限（F-03 §5「按权限决定明文 / 脱敏」）。 */
    private static final String REVEAL_PERMISSION = "org:person:write";

    private final PersonService persons;
    private final ObjectProvider<AuthzSnapshotClient> snapshots;

    public PersonController(PersonService persons, ObjectProvider<AuthzSnapshotClient> snapshots) {
        this.persons = persons;
        this.snapshots = snapshots;
    }

    @GetMapping
    @RequirePermission("org:person:read")
    public PageResult<PersonView> list(@RequestParam(required = false) String q,
                                       @RequestParam(required = false) PersonType type,
                                       @RequestParam(required = false) PersonStatus status,
                                       @RequestParam(required = false) String orgUnitId,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return persons.search(q, type, status, orgUnitId, page, size);
    }

    @GetMapping("/{id}")
    @RequirePermission("org:person:read")
    public PersonView detail(@PathVariable String id) {
        return persons.detail(id, revealSensitive());
    }

    @PostMapping
    @RequirePermission("org:person:write")
    @ResponseStatus(HttpStatus.CREATED)
    public PersonView create(@RequestBody PersonCreateCmd cmd) {
        return persons.create(cmd);
    }

    @PutMapping("/{id}")
    @RequirePermission("org:person:write")
    public PersonView update(@PathVariable String id, @RequestBody PersonUpdateCmd cmd) {
        return persons.update(id, cmd);
    }

    @DeleteMapping("/{id}")
    @RequirePermission("org:person:write")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@PathVariable String id) {
        persons.disable(id);
    }

    /** 数字员工档案维护（按外部 ID 幂等，F-03 §3）。 */
    @PutMapping("/by-external/{source}/{externalId}")
    @RequirePermission("org:person:write")
    public PersonView maintainDigitalEmployee(@PathVariable String source, @PathVariable String externalId,
                                              @RequestBody DigitalEmployeeCmd cmd) {
        return persons.maintainDigitalEmployee(source, externalId, cmd);
    }

    @PutMapping("/{id}/report-line")
    @RequirePermission("org:person:write")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateReportLine(@PathVariable String id, @RequestBody ReportLineCmd cmd) {
        persons.updateReportLine(id, cmd);
    }

    @GetMapping("/{id}/changes")
    @RequirePermission("org:read")
    public List<OrgChangeView> changes(@PathVariable String id,
                                       @RequestParam(required = false) Integer limit) {
        return persons.changes(id, limit);
    }

    /** 快照缺失或回源失败时降级为脱敏，不阻断详情查询。 */
    private boolean revealSensitive() {
        AuthzSnapshotClient client = snapshots.getIfAvailable();
        if (client == null) {
            return false;
        }
        Principal principal = PrincipalHolder.current().orElse(null);
        if (principal == null || principal.personId() == null) {
            return false;
        }
        try {
            AuthzSnapshot snapshot = client.load(principal.personId());
            return snapshot != null && snapshot.has(REVEAL_PERMISSION);
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
