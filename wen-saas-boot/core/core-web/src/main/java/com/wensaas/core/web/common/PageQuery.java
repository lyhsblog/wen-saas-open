package com.wensaas.core.web.common;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

/**
 * 通用分页查询请求。
 *
 * <p>1-based 页码；size 上限 200；sort 形如 {@code field,asc|desc}，可多次。
 *
 * <p>controller 用法：方法参数声明 {@code @Valid @ParameterObject PageQuery pageQuery}，
 * wire 参数名固定为 {@code page} / {@code size}（不用 pageSize），越界返回 422 + errors[]。
 */
public record PageQuery(
        @Parameter(description = "页码，1-based，默认 1") @Min(1) Integer page,
        @Parameter(description = "每页条数，默认 20，上限 200") @Min(1) @Max(200) Integer size,
        @Parameter(description = "排序，形如 field,asc|desc，可多值") List<String> sort) {

    public PageQuery {
        if (page == null) page = 1;
        if (size == null) size = 20;
        if (sort == null) sort = List.of();
    }

    public int offset() {
        return (page - 1) * size;
    }

    /** 解析 {@code field,asc|desc}。无逗号默认 asc；非法 direction 忽略归 asc。 */
    public List<SortClause> parseSort() {
        return sort.stream().map(SortClause::parse).toList();
    }

    public record SortClause(String field, boolean asc) {
        public static SortClause parse(String raw) {
            int i = raw.indexOf(',');
            if (i < 0) return new SortClause(raw.trim(), true);
            String f = raw.substring(0, i).trim();
            String d = raw.substring(i + 1).trim();
            return new SortClause(f, !"desc".equalsIgnoreCase(d));
        }
    }
}
