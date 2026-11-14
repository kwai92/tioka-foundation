package com.foundation.notify.application;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.notify.api.MessageTemplateCmd;
import com.foundation.notify.api.MessageTemplateView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 渠道模板应用服务（F-09 BAS-16 §接口 {@code /api/fdn/message-templates}）。 */
class MessageTemplateServiceTest {

    private final TestDoubles.InMemoryMessageTemplateRepository templates =
            new TestDoubles.InMemoryMessageTemplateRepository();
    private final MessageTemplateService service = new MessageTemplateService(templates);

    @Test
    void duplicateTripleThrowsConflict() {
        service.create(new MessageTemplateCmd("fdn", "code_1", "MAIL", "标题", "正文", null));

        assertThatThrownBy(() -> service.create(new MessageTemplateCmd("fdn", "code_1", "MAIL", "标题2", "正文2", null)))
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void updateMissingThrowsNotFound() {
        assertThatThrownBy(() -> service.update("mtp_missing",
                new MessageTemplateCmd("fdn", "code_1", "MAIL", "标题", "正文", null)))
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void updateChangesTitleVisibleInList() {
        MessageTemplateView created = service.create(
                new MessageTemplateCmd("fdn", "code_1", "MAIL", "旧标题", "正文", null));

        service.update(created.id(), new MessageTemplateCmd("fdn", "code_1", "MAIL", "新标题", "正文2", null));

        assertThat(service.list("fdn", "MAIL")).extracting(MessageTemplateView::titleTpl).containsExactly("新标题");
    }
}
