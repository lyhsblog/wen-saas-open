#!/usr/bin/env bash
# 首次部署：生成自签证书（浏览器会警告，可后续换 Let's Encrypt 正式证书到 deploy/docker-prod/ssl/）
set -euo pipefail
DIR="$(cd "$(dirname "$0")/.." && pwd)"
SSL="$DIR/ssl"
mkdir -p "$SSL"
if [[ -f "$SSL/fullchain.pem" && -f "$SSL/privkey.pem" ]]; then
  echo "ssl/ 已有证书，跳过"
  exit 0
fi
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout "$SSL/privkey.pem" -out "$SSL/fullchain.pem" \
  -subj "/CN=567cc.cc" \
  -addext "subjectAltName=DNS:567cc.cc,DNS:*.567cc.cc,DNS:ops.567cc.cc,DNS:m.567cc.cc,DNS:*.m.567cc.cc"
echo "已写入 $SSL/fullchain.pem 与 privkey.pem（自签）"
