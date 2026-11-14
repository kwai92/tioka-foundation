package com.foundation.notify.api;

import java.util.List;

/**
 * 消息分页视图（F-09 BAS-16 §接口）：游标分页，{@code nextCursor} 为空表示已到末页。
 *
 * @param items      当前页消息
 * @param nextCursor 下一页游标，可空
 */
public record MessagePageView(List<MessageView> items, String nextCursor) {
}
