package com.wensaas.core.impl.compliance.export;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** {@code sys_export_request} Mapper。租户隔离表；运营跨租户查询用 InterceptorIgnoreHelper。 */
@Mapper
public interface ExportRequestMapper extends BaseMapper<ExportRequest> {
}
