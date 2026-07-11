package com.wensaas.core.web.ops.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/** 系统参数批量更新请求体（限定单分组）。 */
public record OpsSettingsUpdateRequest(
        @Schema(description = "(key, value) 映射；value 类型与 sys_setting 种入的 jsonb 一致")
        @NotNull
        Map<String, Object> values
) {}
