# Wen SaaS Frontend

Wen SaaS Boot 的前端 monorepo 工程骨架，承载运营后台、租户后台 Web 与 H5 三个独立入口。

## 目录布局

```text
apps/
  ops-web/       运营后台 Web
  tenant-web/    租户后台 Web
  h5/            移动端 H5
packages/
  api-client/
  auth/
  config/
  design-system/
  domain/
  module-runtime/
  permissions/
  shared-ui/
  utils/
modules/
  base/
tests/
  e2e/
```

## 环境

- Node.js 24.x
- pnpm 11.2.2，通过 Corepack 启用

```powershell
corepack prepare pnpm@11.2.2 --activate
pnpm install
```

## 常用脚本

```powershell
pnpm dev:ops       # http://localhost:5173
pnpm dev:tenant    # http://localhost:5174
pnpm dev:h5        # http://localhost:5175

pnpm build:ops
pnpm build:tenant
pnpm build:h5

pnpm lint
pnpm typecheck
pnpm format
pnpm test
```
