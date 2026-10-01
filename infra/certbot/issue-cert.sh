#!/usr/bin/env bash
# Let's Encrypt 인증서 최초 발급 (HTTP-01, webroot). 80 포트로 nginx가 떠 있고 도메인이 이 서버를 가리켜야 한다.
# 사용법(EC2, 저장소 루트 기준): ./infra/certbot/issue-cert.sh fashion-app-jh.duckdns.org
# 이메일: 공개 저장소에 주소를 남기지 않도록 기본은 미등록. 만료 알림을 받으려면 발급 후 서버에서 한 번 실행:
#   docker run --rm -v "$PWD/certbot/conf:/etc/letsencrypt" certbot/certbot update_account --email <주소> --no-eff-email --agree-tos
# (미등록 상태면 만료 알림 메일이 오지 않으므로 renew-certs.sh를 반드시 cron에 등록할 것)
set -euo pipefail

DOMAIN="${1:?usage: issue-cert.sh <domain>}"
cd "$(dirname "$0")/../.."
mkdir -p certbot/conf certbot/www

docker run --rm \
  -v "$PWD/certbot/conf:/etc/letsencrypt" \
  -v "$PWD/certbot/www:/var/www/certbot" \
  certbot/certbot certonly --webroot -w /var/www/certbot \
  -d "$DOMAIN" --agree-tos --register-unsafely-without-email --non-interactive
