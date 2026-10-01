# Docker 生产部署（567cc.cc）

全栈在 Docker 内运行：`prod` 后端 + PostgreSQL / Redis / RocketMQ + 统一 Nginx 入口（80/443）。

## DNS（解析到你的公网 IP）

| 记录 | 类型 | 说明 |
|------|------|------|
| `ops.567cc.cc` | A | 运营后台 |
| `567cc.cc` | A | 租户裸域（可选，选择页/落地） |
| `*.567cc.cc` | A | 各租户 PC 端，如 `acme.567cc.cc` |
| `m.567cc.cc` | A | H5 裸域 |
| `*.m.567cc.cc` | A | 各租户 H5，如 `acme.m.567cc.cc` |

通配记录若面板不支持，需为每个租户单独加 A 记录。

## 启动

```bash
cd deploy/docker-prod
cp .env.example .env
# 编辑 .env：POSTGRES_PASSWORD、REDIS_PASSWORD、四项 WEN_SAAS_BOOT_* 密钥

chmod +x scripts/init-ssl.sh && ./scripts/init-ssl.sh

sg docker -c 'docker compose up -d --build'
```

与 dev 全容器（`open-docs/非程序员体验依赖/docker`）**不要同机同时跑**（争 80/443）。

## 访问

| 端 | URL |
|----|-----|
| 运营 | https://ops.567cc.cc/login |
| 租户 | https://{租户编码}.567cc.cc/login |
| H5 | https://{租户编码}.m.567cc.cc/login |

初始运营账号：`admin@ops` / `admin123`

源站使用 **`scripts/init-ssl.sh` 生成的自签证书**（`ssl/fullchain.pem`）。浏览器直连源 IP:443 会报证书不受信任；**访客走 Cloudflare 代理时看到的是 Cloudflare 的证书**，一般无需在浏览器里信任自签。

## Cloudflare（DNS 橙云代理）

典型 **重定向死循环** 原因：Cloudflare 设为 **Flexible**（访客↔CF 走 HTTPS，CF↔源站只走 HTTP:80），而源站 Nginx 对 80 一律 `301 → https://`，CF 反复回源 80 → 无限 301。

当前 `nginx-edge.conf` 已处理：当回源请求带 **`X-Forwarded-Proto: https`**（CF 默认会带）时，**在 80 端口直接提供站点**，不再 301。

建议在 Cloudflare 控制台：

| 项 | 推荐 |
|----|------|
| SSL/TLS 加密模式 | **Full**（源站自签即可，CF 不校验源站证书）或 **Full (strict)** + [源站证书](https://developers.cloudflare.com/ssl/origin-configuration/origin-ca/) 替换 `ssl/` |
| 避免 | **Flexible**（除非只开放 80 且接受上面逻辑；仍推荐改 Full） |
| 回源端口 | Full 时可只开 **443**，或 80+443 均可 |

改完 Nginx 配置后：`sg docker -c 'docker compose exec gateway nginx -t && docker compose exec gateway nginx -s reload'`（配置已挂载进容器，无需重建镜像）。

## 换正式 HTTPS（Let's Encrypt 示例）

1. 临时注释 `nginx-edge.conf` 里 80→301，改为 webroot 校验（已留 `/.well-known/acme-challenge/`）。
2. `certbot certonly --webroot -w ./certbot-www ...`
3. 把证书链复制到 `ssl/` 并重启 gateway。

## 文件存储

`WEN_SAAS_BOOT_FILE_PLATFORM_DEFAULT_BACKEND=local`，数据在 Docker 卷 `backend-files`。多机扩展需改 OSS 并去掉 local。
