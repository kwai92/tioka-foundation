package com.foundation.starter.client.dict;

/**
 * 字典项（F-07 §7）。
 *
 * @param typeCode 字典类型编码
 * @param itemCode 项编码
 * @param itemName 项名称
 * @param sort     排序
 * @param appCode  归属应用编码；空表示全局
 */
public record DictItem(String typeCode, String itemCode, String itemName, int sort, String appCode) {
}
