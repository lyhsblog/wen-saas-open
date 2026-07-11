package com.wensaas.core.impl.customfield.quota;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * sys_custom_field_quota Mapper。
 *
 * <p>并发安全的额度增减走 atomic UPDATE WHERE used &lt; quota 模式。
 */
@Mapper
public interface SysCustomFieldQuotaMapper extends BaseMapper<SysCustomFieldQuota> {

    /** 尝试 field_used++ ；仅当 field_used &lt; field_quota 时生效。@return 影响行数（0 或 1） */
    int tryIncFieldUsed(@Param("tenantId") long tenantId);

    /** 尝试 index_used++ ；仅当 index_used &lt; index_quota 时生效。 */
    int tryIncIndexUsed(@Param("tenantId") long tenantId);

    /** field_used-- ；仅当 field_used &gt; 0 时生效。 */
    int decFieldUsed(@Param("tenantId") long tenantId);

    /** index_used-- ；仅当 index_used &gt; 0 时生效。 */
    int decIndexUsed(@Param("tenantId") long tenantId);
}
