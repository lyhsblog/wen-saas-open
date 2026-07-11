package com.wensaas.core.impl.audit.log;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysLoginLogMapper extends BaseMapper<SysLoginLog> {

    int insertBatch(@Param("list") List<SysLoginLog> list);
}
