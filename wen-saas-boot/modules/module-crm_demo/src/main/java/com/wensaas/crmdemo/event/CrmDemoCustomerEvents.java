package com.wensaas.crmdemo.event;

import com.wensaas.crmdemo.entity.CrmDemoCustomer;

/**
 * 客户领域事件：事件 code 常量 + payload 形态（{@code DomainEventPublisher.publish} 的第二参）。
 *
 * <p>code 遵循 {@code {module}.{noun}_{verb}} 惯例，外部系统可经 webhook 按 code 精确订阅。
 * payload 走「瘦身」路线：只带 id 与少量标准字段摘要，<b>不含自定义字段</b>（可能承载敏感业务数据，
 * 且回调体会原样外发）；订阅方需要全量时按 {@code customerId} 回查。id 序列化为字符串，
 * 避免 JS 消费方解析 19 位雪花数丢精度。
 */
public final class CrmDemoCustomerEvents {

    public static final String CUSTOMER_CREATED = "crm_demo.customer_created";
    public static final String CUSTOMER_UPDATED = "crm_demo.customer_updated";
    public static final String CUSTOMER_DELETED = "crm_demo.customer_deleted";
    public static final String CUSTOMER_STAGE_CHANGED = "crm_demo.customer_stage_changed";

    private CrmDemoCustomerEvents() {
    }

    /** created / updated / deleted 三事件共用的客户摘要 payload。 */
    public record CustomerPayload(String customerId, String name, String code, String status) {

        public static CustomerPayload of(CrmDemoCustomer r) {
            return new CustomerPayload(String.valueOf(r.getId()), r.getName(), r.getCode(), r.getStatus());
        }
    }

    /** 阶段推进事件 payload：摘要 + 流转前后状态。 */
    public record StageChangedPayload(String customerId, String name, String code,
                                      String fromStatus, String toStatus) {

        public static StageChangedPayload of(CrmDemoCustomer r, String fromStatus, String toStatus) {
            return new StageChangedPayload(String.valueOf(r.getId()), r.getName(), r.getCode(),
                    fromStatus, toStatus);
        }
    }
}
