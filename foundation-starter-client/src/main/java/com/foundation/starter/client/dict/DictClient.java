package com.foundation.starter.client.dict;

import java.util.List;

/**
 * 字典读取（F-07 §7）：**全局项 + 本应用项，应用项优先**（F-02 §BAS-09）。
 */
public interface DictClient {

    /** 取字典项；全局项与本应用项合并，应用项优先。 */
    List<DictItem> items(String typeCode);
}
