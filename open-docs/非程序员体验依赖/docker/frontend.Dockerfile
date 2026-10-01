# syntax=docker/dockerfile:1
# 前端镜像：容器内 pnpm 构建静态产物 + nginx 运行，本机无需 Node / pnpm。
# 由同目录 docker-compose.yml 使用（构建上下文 = wen-saas-frontend/，剔除项见同目录
# frontend.Dockerfile.dockerignore）。三端共用本文件，构建参数区分：
#   BUILD_SCRIPT=build:ops|build:tenant|build:h5，DIST_DIR=ops-web|tenant-web|h5，
#   VITE_*=各端域名口径（构建参数在 RUN 期即环境变量，Vite 里优先于 .env 文件，
#   以本地地址覆盖 .env.production 里的线上域名）。改哪端只需重建哪端的镜像。
#
# 缓存设计：pnpm store 走 BuildKit cache mount，依赖只在首次构建时下载；
# `pnpm fetch` 层只依赖 lockfile，源码改动不触发重新拉取。三端镜像共享安装层，
# 只有各自的构建步骤分开执行。
# 依赖默认走 npm 官方 registry；需要国内镜像时构建传参：
#   NPM_REGISTRY=https://registry.npmmirror.com

FROM node:24-bookworm AS build
ARG NPM_REGISTRY=https://registry.npmjs.org
# 写入 /root/.npmrc，npm（装 pnpm）与 pnpm（fetch/install）都读它
RUN npm config set registry ${NPM_REGISTRY} && npm i -g pnpm@11.2.2 && pnpm config set store-dir /pnpm-store
WORKDIR /build
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN --mount=type=cache,target=/pnpm-store pnpm fetch
COPY . .
RUN --mount=type=cache,target=/pnpm-store pnpm install --prefer-offline --no-frozen-lockfile
ARG VITE_TENANT_BASE_DOMAIN
ARG VITE_TENANT_WEB_ORIGIN
ARG VITE_H5_WEB_ORIGIN
ARG BUILD_SCRIPT
RUN pnpm run ${BUILD_SCRIPT}

FROM nginx:alpine
ARG DIST_DIR
# 站点配置模板由 compose 挂载同目录 nginx.conf 到 /etc/nginx/templates/（镜像 entrypoint
# 按 NGINX_PORT 环境变量渲染监听端口，与宿主机映射同端口）。
# 先清掉镜像默认站点，避免 80 端口的 default 页与三端站点并存。
ENV NGINX_PORT=80
RUN rm /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist/${DIST_DIR} /usr/share/nginx/html
