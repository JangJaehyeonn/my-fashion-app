# DuckDNS auto-update (EC2 boot)

Keeps `fashion-app-jh.duckdns.org` pointed at this EC2 instance's current public IP.
Runs once automatically every time the instance boots (systemd oneshot service),
so a stop/start (which changes the public IP unless an Elastic IP is attached)
doesn't leave the domain stale.

## Setup (run once on the EC2 instance via SSH)

```bash
cd ~/my-fashion-app
git pull origin dev
chmod +x infra/duckdns/update-duckdns.sh

# 1. Create the token file OUTSIDE the repo — never commit the real token.
sudo mkdir -p /etc/duckdns
sudo tee /etc/duckdns/duckdns.env > /dev/null <<'EOF'
DUCKDNS_DOMAIN=fashion-app-jh
DUCKDNS_TOKEN=610403c9-b7d4-41ab-9019-6cd38f72d3e8
EOF
sudo chmod 600 /etc/duckdns/duckdns.env
sudo chown root:root /etc/duckdns/duckdns.env

# 2. Install the systemd unit
sudo cp infra/duckdns/duckdns-update.service /etc/systemd/system/duckdns-update.service
sudo systemctl daemon-reload
sudo systemctl enable duckdns-update.service

# 3. Test it immediately (don't wait for a reboot)
sudo systemctl start duckdns-update.service
systemctl status duckdns-update.service
journalctl -u duckdns-update.service -n 20
```

A successful run logs `duckdns update response: OK` in the journal.

## Notes

- The unit's `ExecStart` path assumes the repo lives at `/home/ubuntu/my-fashion-app`
  (matches the `git pull` path used in `.github/workflows/deploy.yml`). Update the
  path in `duckdns-update.service` if the EC2 checkout ever moves.
- `infra/duckdns/duckdns.env.example` is a template only — the real token stays in
  `/etc/duckdns/duckdns.env` on the instance and is never committed.
- If a **static Elastic IP** is later attached to the instance, this service becomes
  unnecessary (the IP no longer changes on stop/start) but is harmless to leave enabled.
- To also catch IP changes without a reboot (e.g. DHCP lease renewal), optionally add a
  matching `.timer` unit; not included here since the ask was boot-time only.
