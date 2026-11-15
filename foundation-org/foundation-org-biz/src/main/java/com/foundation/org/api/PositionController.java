package com.foundation.org.api;

import com.foundation.org.api.dto.PageResult;
import com.foundation.org.api.dto.PositionCmd;
import com.foundation.org.api.dto.PositionView;
import com.foundation.org.application.PositionService;
import com.foundation.starter.security.permission.RequirePermission;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 岗位 REST（F-03 §5）。
 *
 * <p>本类不标 {@code @RestController}：由 {@code OrgAutoConfiguration} 以 {@code @Bean} 装配。
 */
@ResponseBody
@RequestMapping("/api/fdn/positions")
@RequirePermission("org:write")
public class PositionController {

    private final PositionService positions;

    public PositionController(PositionService positions) {
        this.positions = positions;
    }

    @GetMapping
    public PageResult<PositionView> list(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return positions.list(page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PositionView create(@RequestBody PositionCmd cmd) {
        return positions.create(cmd);
    }

    @PutMapping("/{id}")
    public PositionView update(@PathVariable String id, @RequestBody PositionCmd cmd) {
        return positions.update(id, cmd);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        positions.delete(id);
    }
}
