# 开放 API 对接

`/api/v1/openapi/**` 是给**外部系统**的接口面：不走用户登录态，用租户自助签发的 API 凭证（AppKey + HMAC 签名）鉴权。网关统一承担鉴权 / 验签 / 防重放 / IP 白名单 / 限流 / 调用留痕，业务模块只需把端点挂到这个前缀下。本篇给两类读者：签发与治理凭证的租户管理员，和实现调用方的外部系统开发者。

## 一、凭证：签发与治理

租户后台「系统管理 → 开放 API」（权限 `api_key.view` 查看 / `api_key.manage` 管理），或直接调 `/api/v1/base/api-keys` 端点：

- **签发**：得到 `apiKey`（`ak_` 前缀公开标识，可随时再查）+ `secret`（HMAC 签名密钥，**仅签发时明文展示一次**，加密落库后无法取回；遗失就轮换）。
- **治理项**：有效期（留空长期）、IP 白名单（单 IP 或 CIDR，仅 IPv4；留空不限）、凭证级限流（每窗口请求数，留空仅受租户级限流）、是否强制验签（默认强制，关闭后调用方可只带 apiKey——仅建议内网低敏场景）。
- **启停 / 轮换即时生效**（管理面主动失效网关缓存）；凭证不删除、只停用，调用日志按凭证关联可溯。
- **调用日志**：管理页「调用日志」tab（或 `GET /api/v1/base/api-keys/call-logs`，权限 `api_key.view`）分页查看本租户的网关调用留痕——时间 / 凭证 / Method / 路径 / 状态码 / 耗时 / 业务模块 / IP / 错误信息，可按凭证、HTTP 状态码、时间窗过滤。无有效凭证的匿名请求（401）归平台侧，不出现在租户视图。

## 二、调用契约（外部系统开发者）

每个请求带四个头：

| Header | 内容 |
|---|---|
| `X-Wen-API-Key` | 凭证的 apiKey |
| `X-Wen-Signature` | 请求签名（见下） |
| `X-Wen-Timestamp` | 发送时刻 Unix 秒（±5 分钟窗口外拒绝） |
| `X-Wen-Nonce` | 每请求唯一随机串（凭证维度去重，防重放） |

签名算法：

```
string_to_sign = METHOD + "\n" + path + "\n" + body + "\n" + timestamp + "\n" + nonce
X-Wen-Signature = Base64( HMAC-SHA256( secret, string_to_sign ) )
```

注意：`path` 是**不含 query string** 的请求路径（如 `/api/v1/openapi/crm_demo/customers`）；`body` 为原始请求体字节（GET 等无体请求用空串）；不做任何 JSON 规范化，发出什么字节就签什么字节。

鉴权失败统一返回 401 + `application/problem+json`，`detail` 不区分具体原因（防探测）；调用方按"检查四个头 + 时钟同步 + secret 是否轮换过"排查。

## 三、限流

两级叠加，**都放行才放行**：租户级（默认 1000 次/60s 窗口，部署方可按租户覆盖）+ 凭证级（签发时配置的每窗口配额）。超限返回 **429**，带 `Retry-After` 与 `X-RateLimit-Limit / X-RateLimit-Remaining / X-RateLimit-Reset` 响应头——正常响应也带这三个头，调用方应据此做客户端节流。

## 四、权限边界（重要）

API 凭证是**租户级机器身份**：开放端点不做行级数据权限（不区分"谁的客户"），返回该租户的开放数据**全量**。访问粒度靠凭证治理（启停 / 有效期 / IP 白名单 / 限流）与套餐控制（未开通对应业务模块的租户调其开放端点返回 402）。因此把 secret 当作数据库口令级别的机密管理：不进代码库、不进前端、按环境隔离，怀疑泄露立即轮换。

## 五、业务端点：CRM Demo 客户（仓内示例）

`module-crm_demo` 提供首个开放业务端点（`OpenApiCrmDemoCustomerController`），采用方照此形态挂自己的端点即可——普通 `@RestController` 放到 `/api/v1/openapi/{module}/` 下、模块描述符 `apiPathPrefixes()` 声明该前缀（套餐门禁与调用日志归类随之生效）、**不挂** `@RequirePermission`/`@DataPermission`（网关鉴权即授权）：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/openapi/crm_demo/customers` | 分页列表（status / source / keyword 筛选，size ≤ 200） |
| GET | `/api/v1/openapi/crm_demo/customers/{id}` | 详情（含自定义字段视图） |
| POST | `/api/v1/openapi/crm_demo/customers` | 建档；**必带 `Idempotency-Key` 头** |

**建档幂等**：`Idempotency-Key` 是调用方自造的唯一键（如自己系统的单号），保留 24 小时；网络重试重复提交时第二次返回 **409**，以首次成功响应为准。

**与 Webhook 配合**（见 [Webhook 事件推送](11-Webhook事件推送.md)）：webhook 回调 payload 只带 id 与摘要，订阅方拿 `customerId` 回查本接口取全量——这是推荐的标准集成模式。

## 六、接入检查清单

1. 租户管理员：后台签发凭证，把 apiKey + secret 安全交付给调用方；
2. 调用方：实现四头签名（注意 path 不含 query、时钟同步、nonce 每请求唯一）；
3. 调用方：写操作带 `Idempotency-Key`；处理 429（按 `Retry-After` 退避）与 401（检查 secret 是否被轮换）；
4. 验证：GET 一次列表接口应 200；改坏签名应 401；同幂等键重复 POST 应 409；
5. 排障：管理页「调用日志」tab 按凭证 / 状态码 / 时间窗过滤，核对状态码、耗时与错误信息。
