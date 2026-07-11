package com.wensaas.core.web.common;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 通用分页响应。
 *
 * <p>{@code total} 为真实 COUNT；不返 {@code totalPages}（前端 {@code Math.ceil(total / size)} 即可）。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class PageResponse<T> {
    private List<T> records;
    private long total;
    private int page;
    private int size;

    public static <T> PageResponse<T> of(List<T> records, long total, PageQuery q) {
        return new PageResponse<>(records, total, q.page(), q.size());
    }

    public static <T> PageResponse<T> empty(PageQuery q) {
        return new PageResponse<>(List.of(), 0L, q.page(), q.size());
    }
}