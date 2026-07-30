#!/usr/bin/env bash
# Updates the DuckDNS A record for DUCKDNS_DOMAIN to this host's current public IP.
# Reads DUCKDNS_DOMAIN / DUCKDNS_TOKEN from the environment (see duckdns.env.example) —
# never hardcode the token here, this file is committed to git.
set -euo pipefail

: "${DUCKDNS_DOMAIN:?DUCKDNS_DOMAIN is not set (check /etc/duckdns/duckdns.env)}"
: "${DUCKDNS_TOKEN:?DUCKDNS_TOKEN is not set (check /etc/duckdns/duckdns.env)}"

response=$(curl -fsS "https://www.duckdns.org/update?domains=${DUCKDNS_DOMAIN}&token=${DUCKDNS_TOKEN}&ip=")

echo "duckdns update response: ${response}"

if [ "${response}" != "OK" ]; then
  echo "duckdns update failed" >&2
  exit 1
fi
