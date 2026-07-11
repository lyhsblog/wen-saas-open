package com.wensaas.core.impl.spi.integration;

import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 集成测试用 stub entity，归属模块 a。
 */
@TableName("a_item")
public class AItem {
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
