package com.wensaas.core.impl.compliance.export;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** {@code sys_data_export_request} Mapper。租户隔离表，随多租户改写自动收敛。 */
@Mapper
public interface DataExportRequestMapper extends BaseMapper<DataExportRequest> {
}
