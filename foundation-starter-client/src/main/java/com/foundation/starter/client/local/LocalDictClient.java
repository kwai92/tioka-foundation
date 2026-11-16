package com.foundation.starter.client.local;

import com.foundation.starter.client.dict.DictClient;
import com.foundation.starter.client.dict.DictItem;

import java.util.List;

/**
 * 字典读取的本地种子实现（ADP-08）：不发起任何 HTTP 调用。
 *
 * <p>「本应用项优先」与类型不存在时返回空列表的语义，与 {@code HttpDictClient} 保持一致，
 * 因此本地模式与中台模式对调用方是同一份契约。
 */
public class LocalDictClient implements DictClient {

    private final LocalFoundationData data;

    public LocalDictClient(LocalFoundationData data) {
        this.data = data;
    }

    @Override
    public List<DictItem> items(String typeCode) {
        return data.dictItems(typeCode);
    }
}
