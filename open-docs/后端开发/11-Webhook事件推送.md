# Webhook 事件推送

业务模块经 `DomainEventPublisher` 发布的领域事件（见 [异步任务与MQ](06-异步任务与MQ.md) §四），底座可以按订阅配置推送到外部系统的回调 URL：统一事件 topic → 订阅匹配扇出（`wen_webhook_send` topic）→ HMAC 签名 HTTP POST → 逐次留痕。**业务模块只负责发事件，推送、签名、重试、日志、订阅管理全部由底座承担**；本篇给两类读者：在租户后台配置订阅的管理员，和实现回调端点的外部系统开发者。

## 一、订阅模型与管理边界

订阅记录在 `sys_webhook` 表，按租户隔离（`tenant_id = 0` 为平台级订阅），核心字段：

| 字段 | 说明 |
|---|---|
| `target_url` | 回调地址，投递时原样 POST |
| `signing_secret_encrypted` | 签名密钥，AES-256-GCM 加密落库（`CredentialCipher`） |
| `subscribed_events` | JSONB 字符串数组，事件 code **精确匹配**（无通配 / 前缀订阅），GIN 索引 |
| `status` | `active` / `disabled` / `deleted`，仅 `active` 参与分发 |

**订阅在租户后台自助管理**：「系统管理 → Webhook 订阅」页（权限 `webhook.view` 查看 / `webhook.manage` 管理），或直接调 `/api/v1/base/webhooks` 端点——新建、编辑、启停、删除、轮换签名密钥、发送测试事件、查投递日志。签名密钥由平台生成，**仅创建 / 轮换时明文展示一次**，加密落库后无法再取回；遗失就轮换（旧密钥立即失效）。回调地址保存时校验：仅 http(s)，默认拒绝私网 / 环回地址（本地联调可配 `wen-saas-boot.webhook.allow-private-targets=true` 放行）。`retry_policy` 列（单订阅重试策略）当前版本不生效，重试一律走全局配置。

## 二、哪些事件可订阅

任意经 `DomainEventPublisher.publish(eventType, payload)` 发出的事件 code 都可订阅，**无需预注册、无白名单**——订阅方把 code 字符串放进 `subscribed_events` 数组即可。code 命名约定 `{module}.{noun}_{verb}`（如 `crm_demo.customer_created`）；业务模块想让某个动作可被外部订阅，发一条领域事件就完成了全部工作。

内建示例：CRM Demo 模块的客户增删改与阶段推进已发四个事件——`crm_demo.customer_created` / `customer_updated` / `customer_deleted` / `customer_stage_changed`（定义见 `module-crm_demo` 的 `CrmDemoCustomerEvents`）。payload 只带 id 与标准字段摘要、不含自定义字段，订阅方需要全量时按 `customerId` 回查。

分发按事件里的 `tenantId` 严格匹配同租户订阅，租户被停用时跳过推送。

## 三、回调收到什么

`POST {target_url}`，`Content-Type: application/json`，body 是完整的事件信封（`EventEnvelope` 的 JSON）：

```json
{
  "tenantId": 1936000000000000001,
  "eventType": "crm_demo.customer_created",
  "eventId": "1936000000000000123",
  "occurredAt": "2026-07-03T10:15:30",
  "requestId": "9f2c...",
  "payload": { "customerId": "1936000000000000456" }
}
```

请求头（header 名可配置，下为默认值）：

| Header | 内容 |
|---|---|
| `X-Wen-Signature` | HMAC-SHA256 签名（见下节） |
| `X-Wen-Timestamp` | 发送时刻 Unix 秒 |
| `X-Wen-Event` | 事件 code |
| `X-Wen-Event-Id` | 事件 id（幂等去重键） |

## 四、回调方怎么验签

签名算法：

```
string_to_sign = "{X-Wen-Event}\n{X-Wen-Timestamp}\n{原始请求体}"
signature      = Base64( HMAC-SHA256( signing_secret, string_to_sign ) )
```

回调端点的标准处理：

1. 用自己保存的 secret 按上式重算，与 `X-Wen-Signature` 恒定时间比对，不一致拒绝；
2. 校验 `X-Wen-Timestamp` 在可接受窗口内（如 ±5 分钟），防重放；
3. 按 `X-Wen-Event-Id` 做幂等——**重试会以同一 eventId 重复投递同一报文**，回调方必须可重入；
4. 尽快返回 2xx（读超时默认 10 秒），耗时处理放异步；非 2xx 会触发重试。

## 五、重试与投递日志

- 非 2xx 响应、连接失败、超时都算失败并重试——**4xx 同样会重试**，回调端点不要用 4xx 表达"收到但不想要"；
- 全局重试次数默认 3，退避默认 `5s → 30s → 5m`（RocketMQ 延时消息实现，进程重启不丢）；
- 每次尝试在 `sys_webhook_log` 独立留痕一行（按月分区）：请求/响应体（默认截断 4000 字节）、HTTP 状态、耗时、`final_status`＝`success` / `failed`（还会重试）/ `abandoned`（重试穷尽）；管理页「投递日志」即查此表；
- **连续失败自动停用**：一个事件重试穷尽记 1 次连续失败（成功一次即清零），达 `auto-disable-threshold`（默认 10）订阅自动置停用并站内信告警租户主管理员；修好回调端点后在管理页重新启用（计数归零）。设 0 关闭该机制；
- MQ at-least-once 的重复投递由幂等守卫去重（同 `webhookId:eventId:attempt` 只发一次 HTTP、写一行日志）；
- 运营端「平台监控」看板有"API · Webhook 概览"卡片：近 7 天推送量、成功率、失败 Top 订阅。

## 六、部署与配置

分发依赖 RocketMQ，需预建 `wen_event`（NORMAL）与 `wen_webhook_send`（**必须 DELAY 类型**，重试靠延时消息，类型不符 broker 会拒收，见 [部署](../部署.md)）两个 topic；两个消费者受 `wen-saas-boot.mq.consumers-enabled` 总开关门控。配置前缀 `wen-saas-boot.webhook`：

| 键 | 默认值 | 说明 |
|---|---|---|
| `enabled` | `true` | 关闭后事件照发、但无 webhook 扇出 |
| `send-topic` | `wen_webhook_send` | 投递队列 topic |
| `sender-consumer-group` | `wen-saas-boot-webhook-sender` | 投递消费组 |
| `http-connect-timeout` / `http-read-timeout` | `3s` / `10s` | 回调超时 |
| `retry-times` | `3` | 重试上限 |
| `retry-backoff` | `[5s, 30s, 5m]` | 退避序列（超出按末项延伸） |
| `body-truncate-bytes` | `4000` | 日志中请求/响应体截断 |
| `signature-header` 等 4 项 | `X-Wen-*` | 四个请求头名 |
| `dedup-ttl` | `10m` | 幂等去重键 TTL |
| `auto-disable-threshold` | `10` | 连续失败自动停用阈值（0=关闭） |
| `allow-private-targets` | `false` | 允许回调地址指向私网/环回（仅本地联调开） |

## 七、安全边界（部署方必读）

- **`target_url` 保存期防 SSRF 校验**：仅 http(s)，主机解析结果命中环回 / 私网 / 链路本地 / IPv6 ULA 即拒绝（`allow-private-targets=true` 可放行，仅限本地联调）。校验发生在保存时，DNS 其后可能变化（TOCTOU）——纵深防御仍建议在出口网络层（安全组 / egress 策略）限制可达网段；
- `webhook.manage` 是敏感权限（回调体含业务负载、可把数据导向外部 URL），只授给需要对接外部系统的管理角色；
- secret 只以密文落库，但投递进程内可解出明文——按机密数据管理数据库与密钥环境变量；
- 回调体里含业务负载（`payload`），订阅方 URL 必须是 HTTPS 生产端点，避免明文外传。

## 八、接入检查清单

1. 业务模块：动作完成处 `eventPublisher.publish("{module}.{noun}_{verb}", payload)`（已发事件的模块无需任何改动）；
2. 部署方：预建两个 topic（`wen_webhook_send` 为 DELAY 类型）；
3. 租户管理员：在「系统管理 → Webhook 订阅」新建订阅（回调 URL + 事件 code 精确列举），保存一次性展示的签名密钥；
4. 回调方：验签 + 时间戳窗口 + 按 `X-Wen-Event-Id` 幂等 + 快速 2xx；
5. 验证：管理页点「发送测试事件」（`webhook.test`），投递日志出现 `success` 行、回调端收到可验签报文；再触发一次真实业务动作（如 crm_demo 建客户）确认业务事件同样送达。
