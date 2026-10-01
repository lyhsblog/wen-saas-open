# syntax=docker/dockerfile:1
# 生产入口：三端静态站 + nginx。构建上下文 = 仓库根目录（见 docker-compose.yml）。

FROM node:24-bookworm AS frontend-build
ARG NPM_REGISTRY=https://registry.npmjs.org
ARG VITE_TENANT_BASE_DOMAIN=567cc.cc
ARG VITE_TENANT_WEB_ORIGIN=https://567cc.cc
ARG VITE_H5_WEB_ORIGIN=https://m.567cc.cc

RUN npm config set registry ${NPM_REGISTRY} && npm i -g pnpm@11.2.2 && pnpm config set store-dir /pnpm-store
WORKDIR /build
COPY wen-saas-frontend/package.json wen-saas-frontend/pnpm-lock.yaml wen-saas-frontend/pnpm-workspace.yaml ./
RUN --mount=type=cache,target=/pnpm-store pnpm fetch
COPY wen-saas-frontend/ .
RUN --mount=type=cache,target=/pnpm-store pnpm install --prefer-offline --no-frozen-lockfile

ENV VITE_TENANT_BASE_DOMAIN=${VITE_TENANT_BASE_DOMAIN}
RUN pnpm run build:tenant

ENV VITE_TENANT_BASE_DOMAIN=m.567cc.cc
RUN pnpm run build:h5

ENV VITE_TENANT_WEB_ORIGIN=${VITE_TENANT_WEB_ORIGIN}
ENV VITE_H5_WEB_ORIGIN=${VITE_H5_WEB_ORIGIN}
RUN pnpm run build:ops

FROM nginx:alpine
RUN mkdir -p /var/www/certbot
COPY --from=frontend-build /build/dist/ops-web /var/www/ops
COPY --from=frontend-build /build/dist/tenant-web /var/www/tenant
COPY --from=frontend-build /build/dist/h5 /var/www/h5
COPY deploy/docker-prod/nginx-edge.conf /etc/nginx/conf.d/default.conf
EXPOSE 80 443
