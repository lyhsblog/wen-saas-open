package com.wensaas.core.impl.compliance.export;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** {@code sys_export_policy} Mapper（每租户单行）。 */
@Mapper
public interface ExportPolicyMapper extends BaseMapper<ExportPolicy> {
}
