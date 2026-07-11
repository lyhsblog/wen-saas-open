# REST 接口约定

写任何对外 HTTP 接口（业务模块或底座扩展）都必须遵守本契约。后端公共实现集中在 `core-web` 的 `com.wensaas.core.web.common` 包（`JsonConfig` / `PageQuery` / `PageResponse` / `ValidationRestExceptionAdvice` / `BaseRestExceptionAdvice`），前端 TS 类型从 OpenAPI 文档生成。

## 一、路径与版本

| 前缀 | 用途 |
|---|---|
| `/api/auth/**` | 登录 / 登出 / 当前主体 |
| `/api/v1/base/**` | 租户后台（底座能力：账号、成员、部门、角色、字典、自定义字段…） |
| `/api/v1/ops/**` | 运营后台 |
| `/api/v1/biz/{module}/**` | 业务模块端点（SPI `apiPathPrefixes()` 声明） |
| `/api/v1/public/**` | 无需认证的公开接口（`@PublicEndpoint` 标注，`core-api` 的 `auth/permission` 包） |
| `/api/v1/me/**` | 当前用户元数据（能力包 / 偏好 / 个人操作日志） |
| `/api/v1/files/**` | 文件上传 / 下载（文件中心管理走 `/api/v1/file-center/**`） |
| `/api/v1/openapi/**` | 开放 API 网关（AppKey 鉴权） |

版本策略：v1 内只做向后兼容变更（新增字段、新增可选参数、新增端点）；字段删除 / 类型变化 / 语义反转才触发 `/v2/`。版本只走路径，不做 Accept 头协商。

## 二、JSON 字段规范

- 命名统一 **camelCase**；
- **ID 一律序列化为字符串**：主键与外键在 Java 里是 `Long`（雪花 ID），全局 `JsonConfig` 把 `Long`/`long` 序列化为 JSON string（雪花 ID 超出 JS safe integer 2^53，用 number 会丢精度）。**前端 TS 类型必须写 `string`，不要转 number**；
- 状态枚举字段对外一律 **string**（如 `"active"`），不暴露数据库的数字编码；
- 时间统一 **ISO-8601 带 `+08:00` 偏移**：`2026-05-22T18:30:00+08:00`。`JsonConfig` 对 `OffsetDateTime` 直接格式化，对 `LocalDateTime` 按 `Asia/Shanghai` 补偏移（DB `TIMESTAMP` 无时区、按北京时间存）。时间区间筛选参数固定 `startAt` / `endAt`（ISO-8601），含两端；
- 可选字段输出 `null`（TS 用 `T | null`），DTO 里不要用 `Optional<T>`；
- 金额用最小货币单位的 `long`（如分）+ 显式 `currency` 字段，不用浮点。

### @RequestBody record 的布尔字段必须用包装类型 Boolean

请求体 record 里写 primitive `boolean` 时，字段缺省会导致反序列化 400。规则：**一律用 `Boolean`，读取时做 null 安全归一**：

```java
public record UpdateRequest(String name, Boolean enabled) {
    public boolean enabledOrDefault() {
        return Boolean.TRUE.equals(enabled);
    }
}
```

（query 参数不受此限制，`@RequestParam(defaultValue = "false") boolean mine` 是合法写法。）

## 三、错误响应模型

所有错误统一返回 RFC 7807 `ProblemDetail`（`Content-Type: application/problem+json`）：

```json
{
  "type": "https://wen-saas-boot/errors/validation",
  "title": "Validation failed",
  "status": 422,
  "detail": "请求字段校验失败",
  "code": "VALIDATION_FAILED",
  "errors": [
    { "field": "username", "message": "不能为空", "code": "NotBlank" }
  ]
}
```

- `code`：业务错误码，全大写下划线（`BAD_CREDENTIALS` / `ROLE_CODE_DUPLICATE`），**前端只匹配 code、不匹配 detail**；
- `errors[]`：仅 422 字段校验失败时附带，`errors[].code` 沿用 Jakarta 注解名（`NotBlank` / `Size` / `Email`…）。

### HTTP 状态码语义

| 状态 | 用途 |
|---|---|
| 200 | 成功（查询、更新、删除返回结果） |
| 201 | 创建成功，响应体含新资源 |
| 204 | 成功无内容（删除、登出） |
| 400 | 业务前置校验失败（service 层主动抛，如配置不合法）；请求体不可解析（`MALFORMED_REQUEST_BODY`） |
| 401 | 未认证 / 凭证失效 |
| 402 | 租户未购买该业务模块（`PACKAGE_UNAUTHORIZED`） |
| 403 | 已认证但无权限；套餐过期只读态下的写请求（`PACKAGE_READONLY`） |
| 404 | 资源不存在，**含跨租户访问**（见下） |
| 409 | 冲突（并发更新 / 唯一约束，如 `ROLE_CODE_DUPLICATE`） |
| 410 | 资源已撤销 / 已过期（如分享链接） |
| 413 | payload 过大（`SIZE_EXCEEDED`） |
| 422 | 字段校验失败（Jakarta Validation），带 `errors[]` |
| 429 | 限流，配 `Retry-After` 响应头 |
| 500 | 后端异常 |

### 422 与 400 的分界（容易混）

| 状态 | 触发来源 | 典型场景 | 前端处理 |
|---|---|---|---|
| **422** | `@Valid` / `@Validated` 校验注解失败，由 `ValidationRestExceptionAdvice` 统一转 422 + `errors[]` | 必填为空、长度超限、格式不匹配 | 按 `errors[].field` 内联到输入框旁 |
| **400** | service 层主动抛业务异常 | 字段格式都对，但语义不合法（如"已存在激活配置"） | toast 显示 `detail` |

业务代码**不要自己 catch 校验异常拼响应**：controller 参数上挂 `@Valid` / 校验注解，剩下交给全局 Advice。模块自定义业务异常配套模块级 `@RestControllerAdvice`，错误码收敛到模块自己的 Exception 枚举。注意模块 Advice 不要写 `Exception` 兜底（兜底 500 由 `BaseRestExceptionAdvice` 负责），否则会抢吃框架异常。

### 跨租户安全约定（重要）

跨租户访问一律返 **404 NOT_FOUND** 而非 403，不暴露资源存在性。前端把它当"资源不存在"处理，不要提示"无权限"。

## 四、分页 / 筛选 / 批量

### 分页请求

```
GET /api/v1/biz/crm_demo/customers?page=1&size=20&sort=createdAt,desc&sort=id,desc
```

- `page`：1-based，默认 1；`size`：默认 20，上限 200（`PageQuery` 上有 `@Max(200)`，超限 422）；
- `sort`：`field,asc|desc`，可重复，后端按出现顺序拼 ORDER BY；
- 复用 `core-web` 的 `PageQuery`（含 `offset()` / `parseSort()` 工具方法）。

### 分页响应

统一 `PageResponse<T>` 形态：

```json
{ "records": [ ... ], "total": 1234, "page": 1, "size": 20 }
```

`total` 为真实 COUNT；不返回 `totalPages`（前端 `Math.ceil(total / size)`）。

### 筛选

- 简单条件平铺 query 参数：`?status=active&keyword=acme`；多值（IN）重复出现：`?status=active&status=pending`；
- 复杂组合筛选走 `POST /api/v1/.../search` + 请求体。

### 批量操作

约定形态（底座当前无内置批量端点，业务模块按此形态实现）：

```
POST /api/v1/.../batch-disable
{ "ids": ["1", "2", "3"] }

200 OK
{ "succeeded": ["1", "3"],
  "failed": [ { "id": "2", "code": "ITEM_LOCKED", "message": "被业务引用" } ] }
```

部分成功也返 200，前端按 `failed` 提示；全失败返业务错误码。

### 软删 / 恢复

- 删除：`DELETE /api/v1/.../{id}`，后端走 `@TableLogic` 软删（`deleted_at = now()`）；
- 恢复：`POST /api/v1/.../{id}/restore`。

## 五、API 动词化规约

**有独立业务语义的动作用动词子资源端点，不用宽泛的字段编辑接口承载状态迁移。** 形态固定：

```
POST /api/v1/.../{id}/<verb>
```

判据：

| 应拆成动词端点 | 不要拆（用编辑端点） |
|---|---|
| 状态迁移：启用/禁用、暂停/恢复、发布/下架、归档 | 同质字段的批量编辑（名称/备注一把改） |
| 独立语义动作：重置密码、强制下线、授予角色 | 改配置、改资料类 PUT/PATCH |
| 需要独立权限点或独立审计语义的动作 | 纯查询 |

- 动词端点只收该动作必需的入参（如 `reason`），不收宽泛的 `status` 标志位；
- 好处：操作日志 action 自描述（记"暂停租户"而非"更新状态"）、权限可独立授权（`xxx.suspend` 与 `xxx.update` 分开）；
- 边界：按"状态机的边 + 独立权限"这把尺子拆，不要为日志好看去拆没有独立语义的动作。

样板参考：`POST /api/v1/biz/crm_demo/customers/{id}/transition`（客户状态机推进）。

## 六、Header 与鉴权注解速查

- 请求：`Authorization: Bearer <token>` 必带（`@PublicEndpoint` 端点除外）；`X-Request-Id` 可选，不传后端生成；**不要传 `X-Tenant-Id`**（租户上下文来自登录态）；
- 响应：后端必回 `X-Request-Id`，前端错误提示里展示便于排查；
- 权限：controller 方法挂 `@RequirePermission("{module}.{menu}.{action}")`（功能权限）与 `@DataPermission("...")`（数据范围过滤，列表/详情查询按需挂），两者独立共存；
- 敏感字段（手机号/邮箱等）在列表响应中默认掩码，完整值走带权限门控的专用端点并记操作日志；一次性凭证（API Key Secret 等）只在创建响应返回一次。
