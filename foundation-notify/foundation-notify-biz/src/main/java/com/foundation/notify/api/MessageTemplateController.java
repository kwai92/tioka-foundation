package com.foundation.notify.api;

import com.foundation.notify.api.MessageTemplateApi;
import com.foundation.notify.api.MessageTemplateCmd;
import com.foundation.notify.api.MessageTemplateView;
import com.foundation.starter.security.permission.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * 渠道模板 REST（F-09 BAS-16 §接口 {@code /api/fdn/message-templates}）：列表、新建与更新。
 *
 * <p>本类不标 {@code @RestController}：由 {@code NotifyAutoConfiguration} 以 {@code @Bean} 装配。
 */
@ResponseBody
@RequestMapping("/api/fdn/message-templates")
public class MessageTemplateController {

    private static final String PERMISSION = "fdn:notify:send";

    private final MessageTemplateApi templates;

    public MessageTemplateController(MessageTemplateApi templates) {
        this.templates = templates;
    }

    /** 模板列表（按应用 / 渠道过滤，均可空）。 */
    @GetMapping
    @RequirePermission(PERMISSION)
    public List<MessageTemplateView> list(@RequestParam(required = false) String appCode,
                                          @RequestParam(required = false) String channel) {
        return templates.list(appCode, channel);
    }

    /** 新建模板。 */
    @PostMapping
    @RequirePermission(PERMISSION)
    public MessageTemplateView create(@RequestBody MessageTemplateCmd cmd) {
        return templates.create(cmd);
    }

    /** 更新模板。 */
    @PutMapping("/{id}")
    @RequirePermission(PERMISSION)
    public MessageTemplateView update(@PathVariable String id, @RequestBody MessageTemplateCmd cmd) {
        return templates.update(id, cmd);
    }
}
