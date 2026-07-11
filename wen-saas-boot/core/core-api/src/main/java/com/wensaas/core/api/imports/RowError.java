package com.wensaas.core.api.imports;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 单行单字段校验错误。
 *
 * @param rowNo   行号（1-based，对应用户 Excel 的数据行序号）
 * @param field   出错字段的 key；整行级错误可为 {@code null}
 * @param code    错误类型
 * @param message 可读错误说明
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class RowError {
    private int rowNo;
    private String field;
    private ImportErrorCode code;
    private String message;

}
