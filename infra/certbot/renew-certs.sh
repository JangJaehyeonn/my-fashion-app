#!/usr/bin/env bash
# 인증서 갱신 + nginx 리로드. 만료 30일 전부터만 실제 갱신되므로 하루 1~2회 돌려도 안전하다.
# cron 예: /etc/cron.d/wearon-certbot  →  17 3 * * * ubuntu /home/ubuntu/my-fashion-app/infra/certbot/renew-certs.sh >> /var/log/wearon-certbot.log 2>&1
set -euo pipefail

cd "$(dirname "$0")/../.."

docker run --rm \
  -v "$PWD/certbot/conf:/etc/letsencrypt" \
  -v "$PWD/certbot/www:/var/www/certbot" \
  certbot/certbot renew --webroot -w /var/www/certbot --quiet

docker compose -f docker-compose.prod.yml exec -T nginx nginx -s reload
