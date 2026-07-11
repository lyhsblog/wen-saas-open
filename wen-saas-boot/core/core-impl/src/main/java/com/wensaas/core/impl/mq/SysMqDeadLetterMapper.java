package com.wensaas.core.impl.mq;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@link SysMqDeadLetter} CRUD。仅 insert + 基础查询（运维列表/重投入口 v2 再扩）。
 */
@Mapper
public interface SysMqDeadLetterMapper extends BaseMapper<SysMqDeadLetter> {
}
