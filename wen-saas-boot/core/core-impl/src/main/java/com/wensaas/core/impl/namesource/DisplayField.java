package com.wensaas.core.impl.namesource;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.wensaas.core.api.namesource.MatchMode;

/**
 * {@link LambdaNameSource} 的一个可搜 / 可显字段：getter（取值 + MP 反解列名）、中文 label、匹配模式。
 * impl 内部结构，不外溢 api（api 的 builder 以散参 {@code getter/label/match} 暴露）。
 *
 * @param <T> 实体类型
 */
record DisplayField<T>(SFunction<T, String> getter, String label, MatchMode match) {}
