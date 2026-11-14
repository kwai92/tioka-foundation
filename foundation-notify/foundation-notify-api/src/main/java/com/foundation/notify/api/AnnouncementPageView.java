package com.foundation.notify.api;

import java.util.List;

/**
 * 公告分页视图（F-09 BAS-16 §接口）：游标分页，{@code nextCursor} 为空表示已到末页。
 *
 * @param items      当前页公告
 * @param nextCursor 下一页游标，可空
 */
public record AnnouncementPageView(List<AnnouncementView> items, String nextCursor) {
}
