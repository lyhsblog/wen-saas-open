package com.wensaas.core.impl.spi.module;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * sys_business_module Mapper。SPI 启动期由 {@link BusinessModuleDbSynchronizer} 使用。
 */
@Mapper
public interface SysBusinessModuleMapper extends BaseMapper<SysBusinessModule> {
}
