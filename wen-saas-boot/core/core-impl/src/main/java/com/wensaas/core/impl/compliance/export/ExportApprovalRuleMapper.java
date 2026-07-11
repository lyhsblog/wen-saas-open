package com.wensaas.core.impl.compliance.export;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** {@code sys_export_approval_rule} Mapper（每租户每导出对象至多一行）。 */
@Mapper
public interface ExportApprovalRuleMapper extends BaseMapper<ExportApprovalRule> {
}
