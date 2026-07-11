package com.wensaas.core.impl.stickynote;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 备忘便利贴 Mapper。TenantLine 自动按 tenant_id 隔离；user_id 在业务层过滤。
 */
@Mapper
public interface StickyNoteMapper extends BaseMapper<StickyNote> {
}
