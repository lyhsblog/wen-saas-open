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

自签证书首次访问需在浏览器信任或换正式证书（覆盖 `ssl/fullchain.pem`、`ssl/privkey.pem` 后 `docker compose restart gateway`）。

## 换正式 HTTPS（Let's Encrypt 示例）

1. 临时注释 `nginx-edge.conf` 里 80→301，改为 webroot 校验（已留 `/.well-known/acme-challenge/`）。
2. `certbot certonly --webroot -w ./certbot-www ...`
3. 把证书链复制到 `ssl/` 并重启 gateway。

## 文件存储

`WEN_SAAS_BOOT_FILE_PLATFORM_DEFAULT_BACKEND=local`，数据在 Docker 卷 `backend-files`。多机扩展需改 OSS 并去掉 local。
