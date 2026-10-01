# Docker 全容器运行

用一条命令把整套系统（后端 + 前端三端 + 全部中间件）在 Docker 里**编译并运行**起来。本机只需要 Docker Desktop，不需要安装 JDK、Maven、Node、pnpm。

适合两类场景：

- **非程序员完整体验**（见 [快速开始（非程序员）](<../快速开始(非程序员).md>)）：让 AI 助手在你电脑上部署和开发，环境全部关进 Docker，最省心；
- 想快速把系统跑起来看看，不打算在本机搭建开发环境。

> 如果你是要长期做开发的工程师，宿主机直跑（本机 JDK/Node，中间件进 Docker）有秒级热更新，迭代更快，见 [快速开始（程序员）](<../快速开始(程序员).md>)。全容器模式的编译全部发生在镜像构建里，不碰你本机的任何目录，两种模式可以共存——但**别同时跑**，都会占用 8080 / 5173–5175 端口。

## 1. 环境要求

| 项 | 要求 |
|---|---|
| Docker Desktop | 较新版本（Compose v2、BuildKit，近两年的版本都满足） |
| 资源 | 建议给 Docker 分配 ≥ 4G 内存；磁盘空闲 ≥ 20G |
| 网络 | 首次启动要拉取基础镜像并下载 Maven / npm 依赖，量不小。依赖下载**默认走官方源**（Maven Central、npm 官方 registry）；基础镜像从 Docker Hub 拉取。境内若下载慢，可在构建时用环境变量改国内镜像（见 §5 常见问题） |

## 2. 一键启动

编排文件都在本文档旁边的 `docker/` 子目录里（`open-docs/非程序员体验依赖/docker/`）。进入该目录执行：

```bash
cd open-docs/非程序员体验依赖/docker
docker compose up -d --build
```

> 本文后续所有 `docker compose …` 命令都在这个目录里执行（compose 从当前目录向上查找配置文件）。

首次运行会依次：拉取基础镜像 → 容器内 Maven 编译后端 → 容器内 pnpm 构建前端三端静态站 → 全部启动。**首次约 10–30 分钟**（大头是下载依赖，取决于网络），之后再启动只需几十秒。

整套编排的形态与生产部署一致：后端单 jar + 三份静态站（nginx 提供页面并把接口反代到后端）。运行常驻内存很小（前端三端合计约几十 MB），构建时会短暂占用较多资源。

| 服务 | 说明 | 宿主机端口 |
|---|---|---|
| `backend` | 后端（容器内 Maven 编译的单 jar） | 8080 |
| `frontend-ops` / `frontend-tenant` / `frontend-h5` | 前端三端（容器内 pnpm 构建，nginx 静态站 + 接口反代） | 5173 / 5174 / 5175 |
| `postgres` / `redis` / `rocketmq-*` | 中间件 | 不映射，仅内部网络 |
| `rocketmq-topic-init` | 一次性初始化容器，跑完即退出 | — |

数据库、Redis、RocketMQ 的端口**不映射到宿主机**，不会和本机已有服务冲突；数据都存在 Docker named volume 里。数据库建库、表结构迁移、种子数据全部自动完成，无需任何手工 SQL。

## 3. 验证与访问

```bash
docker compose ps                                # backend 与三个 frontend-* 应为 Up
curl http://localhost:8080/actuator/health       # 返回 {"status":"UP",...} 即后端就绪
```

后端首次启动要先跑完数据库迁移，`up` 返回后再等半分钟左右；`docker compose logs backend` 里看到启动横幅 `Wen SaaS Boot v… 已就绪` 即成功。

浏览器访问（`*.localhost` 域名现代浏览器自动指向本机，无需改 hosts）：

| 端 | 地址 |
|---|---|
| 运营后台 | `http://ops.localhost:5173/login` |
| 租户后台 | `http://{租户编码}.localhost:5174/login` |
| 移动端 H5 | `http://{租户编码}.m.localhost:5175/login` |

初始运营超管：账号 `admin@ops`，密码 `admin123`（首次登录后请修改）。租户需在运营后台开通后才能登录租户端，开通与登录流程见[快速开始（程序员）§6](<../快速开始(程序员).md>)（两种模式此部分完全一致）。

## 4. 日常操作

```bash
# 看日志（-f 跟随；服务名换成 frontend-ops / postgres 等同理）
docker compose logs -f backend

# 修改了后端代码 → 重新编译并重启后端（依赖有缓存，只重新编译）
docker compose up -d --build backend

# 修改了前端代码 → 重建对应端（改哪端建哪端，依赖有缓存、通常几十秒内；重建后刷新浏览器即可）
docker compose up -d --build frontend-tenant     # 另有 frontend-ops / frontend-h5

# 前端新增依赖（改了 package.json）→ 同样重建对应端，构建时会自动解析安装

# 进入数据库排查
docker compose exec postgres psql -U postgres -d wen_saas

# 停止（保留数据）／再次启动
docker compose stop
docker compose up -d

# 彻底重置：停止并删除所有容器与数据卷（数据库、文件、MQ 数据全部清空）
docker compose down -v
```

## 5. 常见问题

**`up` 时报端口被占用（8080 / 5173–5175）**
- 本机有别的进程（或宿主机直跑模式的后端/前端）占了这些端口，停掉后重试。

**后端容器反复重启，日志里连不上 postgres / redis / rocketmq-proxy**
- 先看中间件是否健康：`docker compose ps` 里 postgres/redis 应为 `healthy`，`rocketmq-topic-init` 应为 `Exited (0)`。
- `docker compose logs rocketmq-topic-init` 末行应为 `all topics ready`；不是的话 `docker compose up -d` 重跑一次。

**首次构建后端很慢或下载超时**
- 依赖默认从 Maven Central / npm 官方拉取；若卡在**拉取基础镜像**（日志停在 `FROM …` / `pulling …`），可给 Docker 的 `daemon.json` 配 `registry-mirrors` 加速 Hub。
- 失败后直接重跑 `docker compose up -d --build`，已下载的依赖有缓存不会重来。

**在境内网络，想用国内依赖镜像加速**
- 构建时设环境变量覆盖默认源（之后重建同理带上）：
  ```bash
  MAVEN_MIRROR=https://maven.aliyun.com/repository/public \
  NPM_REGISTRY=https://registry.npmmirror.com \
  docker compose up -d --build
  ```

**前端页面打得开但接口全部报错**
- 看后端是否就绪：`curl http://localhost:8080/actuator/health`；未就绪先看 `docker compose logs backend`。

**改了前端代码，页面上没变化**
- 静态模式下前端改动不会自动生效，需要重建对应端：`docker compose up -d --build frontend-xxx`，然后刷新页面（页面入口已禁缓存，普通刷新即可）。

**想把数据库暴露给本机图形化工具**
- 默认不映射端口。可临时用 `docker compose exec postgres psql ...`，或自行在 `docker-compose.yml` 的 `postgres` 服务加 `ports: ['5432:5432']`（注意别与本机已有 PostgreSQL 冲突）。
