# E2E（Playwright）

跨端浏览器端到端用例。首套定位 **CI 友好的自洽冒烟**：tenant-web 跑在 MSW mock 模式，零后端依赖。

## 跑

```bash
pnpm test:e2e          # 无头跑全部（Playwright 自动起 tenant-web mock dev server）
pnpm test:e2e:ui       # 交互式 UI 模式调试
pnpm test:e2e:report   # 看上次 HTML 报告
```

Playwright 的 `webServer` 会用 `VITE_API_MODE=mock corepack pnpm dev:tenant` 起 5174 端口；
本地若已手动起着 tenant-web（非 CI），会复用（`reuseExistingServer`）——
**注意**：手动起的若不是 mock 模式，登录后会打真后端，用例不稳。要么先停掉，要么用 `CI=1 pnpm test:e2e` 强制新起。

## 为什么走 MSW mock（自洽隔离）

dev 默认走 vite proxy 打本机真后端 8080。mock 模式（`VITE_API_MODE=mock`）改由 MSW Service Worker 拦截 `/api`：

- **handler 必须覆盖登录落地页的全部端点**，否则未处理请求被配成 `onUnhandledRequest:'bypass'` → 旁路到真后端 → 真后端对 mock 假 token 返 401 → 前端全局拦截器登出 → 弹回登录页，污染用例。
- 已为落地面补齐 handler：`auth/capabilities/dict/dept/role/opsProfile` + 新增 `workbench`(overview/cards) + `messageInbox`(unread-count)，见 `packages/api-client/src/mocks/handlers/`。
- ⚠️ Playwright 的 `page.route` **抓不到 Service Worker 发起的** bypass 请求，所以隔离只能靠 handler 全覆盖，不能靠网络层 abort 兜底。新增页面进 E2E 前，先确认其首屏端点都有 mock handler。

mock 种子凭据 / 数据见 `packages/api-client/src/mocks/data.ts`（`alice / P@ssw0rd`）。

## 选择器约定（踩点）

- 侧栏菜单项是自定义 `div`（非 antd `menuitem` role）→ 用 `getByRole('navigation').getByText('字典管理')`。
- AntD 6 对 CJK 按钮文案会插空格（`登 录`）→ 按钮名用 `/登\s*录/` 正则兜底。
- 字典 spec 用 SPA 内导航（点侧栏菜单）只是为了顺带覆盖真实菜单跳转；**硬刷新 / 硬 `goto` 受保护深链接本身是 OK 的**——zustand v5 从 localStorage **同步** rehydrate，首帧 token 就已就位，`RequireAuth` 不会误判（实测 `page.reload()` 工作台、`goto` 字典页均保持登录）。
  - 附：曾一度以为「硬 goto 被弹登录」是 rehydration 竞态，实为 **mock handler 没覆盖落地页端点**（unread-count 轮询旁路真后端拿 401 触发登出）；补齐 handler 即消失。所以新页面进 E2E 前，务必先确认其首屏端点都有 mock handler。

## 真后端 live 套件（已落地）

需要真实数据流的用例放 `tests/e2e/live/**`，跑真后端（**不进默认 CI**，太重）。独立配置
`playwright.live.config.ts`（baseURL 5174、串行、不配 webServer、复用已起的真后端 dev server），
默认 `playwright.config.ts` 已 `testIgnore: tests/e2e/live/**` 与之隔离。

四套 spec（`pnpm test:e2e:live`，共 21 用例）：

| spec                        | 覆盖                                                                        | 形态                               |
| --------------------------- | --------------------------------------------------------------------------- | ---------------------------------- |
| `permission-matrix.spec.ts` | 权限矩阵/数据范围/跨端隔离（A1-PKG/A2/A1-TenantWrite/A3/A4）                | 接口级（`request` 直打 8080）      |
| `ops-menu-matrix.spec.ts`   | [A1-菜单] CSM ops 侧栏菜单收敛                                              | 浏览器（ops-web 5173）             |
| `tenant-lifecycle.spec.ts`  | D1 新租户全生命周期 + [D1-套餐]/[D1-激活链接] + D3 部门/成员/字典导入全链路 | API + 1 浏览器步（共用一次性租户） |
| `ticket-fullchain.spec.ts`  | D2 工单两端全链路 + 附件 + `base.ticket.view_all`                                | 浏览器（双端）                     |

### 本地跑

前置（本机）：8080 jar + docker PG/Redis/RocketMQ 常驻；两端真后端 dev server（`pnpm dev:ops` 5173 /
`pnpm dev:tenant` 5174，默认即 proxy 打 8080，**勿带** `VITE_API_MODE=mock`）；夹具账号齐备。

```bash
pnpm seed:live          # 幂等重建全部夹具（BIZ_TEST_PKG / ops-csm 名下 wmtest /
                        # wmtest / uitest+uiadmin / member1 / dictonly+DICT_READONLY），已存在则跳过
pnpm test:e2e:live      # 跑四套 live spec
pnpm test:e2e:live permission-matrix   # 只跑某一套
```

> 夹具种子脚本 `scripts/seed-live-fixtures.mjs` 也是「换库 / 清库后重建账号」脚本，幂等可反复跑。

### nightly（self-hosted runner）

`.github/workflows/nightly-live.yml`：cron 每日北京 02:00 + 手动 `workflow_dispatch`，`runs-on: self-hosted`。
流程：检查 8080 → `pnpm seed:live` → 复用/临时起两端 dev server → `pnpm test:e2e:live` → 传报告。

**首次启用需在常驻 jar/docker 的那台机器上注册一个 GitHub Actions self-hosted runner（一次性）**：

1. GitHub 仓库 → **Settings → Actions → Runners → New self-hosted runner**，选机器 OS（本机 macOS arm64）。
2. 按页面指引在该机器跑 `./config.sh --url <repo> --token <token>`，交互提示逐个这样答：
   - **runner group** → **直接回车用 `Default`**（group 是组织/企业版功能，个人仓只有 Default，
     填别的名字会报 `Could not find any self-hosted runner group`）；
   - **runner name** → 自定义（如 `wen-mac-runner`）或回车用主机名；
   - **additional labels** → 回车跳过（默认已带 `self-hosted`，workflow 就按这个 label 选）；
   - **work folder** → 回车（`_work`）。
     > registration token 只活约 1 小时，过期就回 Settings→Actions→Runners→New 重新拿。
3. 跑测时该机器需满足：**8080 jar + docker PG/Redis/RocketMQ 常驻**（dev server 不必常驻，workflow 会临时起、
   跑完只清自己起的；已在跑则复用、不动）。
4. 后台常驻 runner：前台 `./run.sh`，或装成服务 `./svc.sh install && ./svc.sh start`。
5. 注册后 nightly 按 cron 自动跑；**首跑建议先在 Actions 页 `Run workflow` 手动触发一次**盯日志
   （尤其种子脚本对 fresh DB 的"创建"分支、RocketMQ/dev-server 起动）。

### 选择器/隔离注意（live 同样适用）

侧栏菜单项是自定义 `div` → `getByRole('navigation').getByText(...)`；AntD6 CJK 按钮文案插空格 → `/登\s*录/` 兜底。
live 不走 MSW，直连真后端；夹具账号由 `scripts/seed-live-fixtures.mjs` 幂等创建（仅本地联调环境有效），接口级断言辅助见 `tests/e2e/helpers.ts`。
