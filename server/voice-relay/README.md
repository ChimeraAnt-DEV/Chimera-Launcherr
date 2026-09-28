# Chimera voice-relay deployment

This is the server half of the launcher's relay voice transport. The launcher still speaks over
LAN multicast by default; pointing it at a relay makes voice work across the internet, with the
server fanning audio out to everyone whose channel can hear the sender's.

The server never decodes audio. It moves opaque payloads, so it does not care whether a client is
sending raw PCM or Opus — it only carries the codec byte through so a listener can tell the two
apart. That is why a 1 vCPU / 1 GB VPS is enough: there is no per-frame codec work, only UDP
read, a channel lookup, and a fan-out write.

## What you need

- A VPS with a public IP. **1 vCPU and 1 GB RAM is plenty to start.** The process is a single
  small binary; it will happily relay a few hundred concurrent talkers.
- A domain name pointing at it. Optional, but recommended: a hostname survives an IP change and
  reads better in the launcher's server field than a raw address. Create an `A` (or `AAAA`)
  record, e.g. `voice.example.com -> 203.0.113.10`.
- One open UDP port. The default is **47902**. TCP 8081 is used only for `/healthz` and
  `/metrics`; you can keep it closed to the internet and reach it over SSH or from your
  monitoring host.
- A firewall that allows that UDP port in. On a typical VPS:

  ```sh
  # ufw
  sudo ufw allow 47902/udp
  # or nftables / iptables
  sudo nft add rule inet filter input udp dport 47902 accept
  ```

  If the VPS is behind a cloud firewall (AWS security group, Oracle, Hetzner), add the UDP rule
  there too — a host firewall alone will not open the port.

## Build

From this directory:

```sh
go build -o voice-relay .
```

Or with Docker:

```sh
docker build -t chimera-voice-relay .
docker run -d --name chimera-voice \
  -p 47902:47902/udp -p 8081:8081/tcp \
  -e VOICE_PASSWORD=change-me \
  chimera-voice-relay
```

## Configure

Every setting has a default, so `./voice-relay` with no arguments runs an open relay on
`:47902`. Settings come from, in increasing precedence: a JSON file (`-config`), environment
variables (`VOICE_*`), and command-line flags. See `config.example.json` for the full set.

The ones that matter in practice:

| Flag | Env | Default | What it does |
| --- | --- | --- | --- |
| `-listen` | `VOICE_LISTEN` | `:47902` | UDP bind address |
| `-http-listen` | `VOICE_HTTP_LISTEN` | `:8081` | health/metrics bind; `""` disables |
| `-public-address` | `VOICE_PUBLIC_ADDRESS` | (bind addr) | address advertised to clients |
| `-password` | `VOICE_PASSWORD` | (open) | shared password; clients must send it |
| `-max-clients` | `VOICE_MAX_CLIENTS` | 200 | concurrent sessions |
| `-max-clients-per-ip` | `VOICE_MAX_CLIENTS_PER_IP` | 8 | per-address sessions; 0 disables |
| `-max-channel-members` | `VOICE_MAX_CHANNEL_MEMBERS` | 50 | per-channel cap; 0 disables |
| `-rate-pps` | `VOICE_RATE_PPS` | 120 | per-client packets/second |
| `-rate-bps` | `VOICE_RATE_BPS` | 32000 | per-client bytes/second |
| `-token-secret` | `VOICE_TOKEN_SECRET` | (off) | shared secret for signed join tokens; see below |
| `-token-ttl-hours` | `VOICE_TOKEN_TTL_HOURS` | 6 | default token lifetime for `-mint-token` |
| `-admin-listen` | `VOICE_ADMIN_LISTEN` | (off) | TCP bind for the admin ban endpoint; never expose publicly |
| `-admin-token` | `VOICE_ADMIN_TOKEN` | (off) | bearer token required by the admin endpoint |
| `-auth-failures-before-ban` | `VOICE_AUTH_FAILURES_BEFORE_BAN` | 5 | auto-ban an IP after this many bad attempts; 0 disables |

### Token auth instead of a password

A plain password travels in the `HELLO`, so anyone who captures it can replay it. Setting
`token_secret` switches the relay to signed join tokens instead: the client holds the secret,
never sends it, and instead presents a short-lived token signed with it. The token also carries
the client's device id, so a captured token cannot be replayed from another device and expires on
its own.

```sh
# -mint-token takes the device id as its value; the printed token is what that device presents.
./voice-relay -mint-token abc123 -token-secret "$(openssl rand -hex 32)"
```

Give players the secret (launcher → Voice tab → **Join token secret**) and set the same
`token_secret` on the server. When `token_secret` is set, tokens are required; the password field
is only used when no secret is configured.

### Bans and the admin endpoint

Bad credentials from one address auto-ban it after `auth_failures_before_ban` attempts for
`ban_minutes`. Permanent bans go in `banned_ips` (a plain IP or CIDR) and `banned_devices`
(survives an IP change). For live control, set `admin_listen` and `admin_token`:

```sh
curl -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"ip":"203.0.113.77","minutes":60}' localhost:8082/admin/ban
curl -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"device":"abc123"}' localhost:8082/admin/ban
curl -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"ip":"203.0.113.77"}' localhost:8082/admin/unban
curl -H "Authorization: Bearer $ADMIN_TOKEN" localhost:8082/admin/bans
```

A ban takes effect immediately, including dropping any live session it matches. **Bind the admin
endpoint to localhost or a private interface** (`127.0.0.1:8082`), or reach it over SSH; it is
guarded only by the bearer token.

The **per-client byte rate is the one that bounds your bandwidth bill.** At the default 32 kB/s a
client can send roughly 4 kB/s of sustained audio, which is generous for 20 ms Opus frames and
still caps a flooder. The packet rate stops a small-packet flood; the byte rate stops a
large-packet one. Both buckets must have a token for a packet to be relayed.

## Run under systemd

```sh
sudo useradd --system --no-create-home --shell /usr/sbin/nologin voice-relay
sudo mkdir -p /opt/chimera-voice /etc/chimera-voice
sudo cp voice-relay /opt/chimera-voice/
sudo cp config.example.json /etc/chimera-voice/relay.json   # edit it
sudo chown -R voice-relay:voice-relay /opt/chimera-voice
sudo cp deploy/chimera-voice-relay.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now chimera-voice-relay
```

Check it:

```sh
systemctl status chimera-voice-relay
journalctl -u chimera-voice-relay -f
curl -s localhost:8081/healthz
```

## Monitoring and logs

- **Logs** are JSON lines on stderr (or the journal), one per event: `client joined`,
  `client left`, `client evicted`, `rejected hello: bad password`, and so on. Each line carries a
  timestamp, a level and the relevant fields, so `journalctl -u chimera-voice-relay | jq` works
  without a parser. Raise verbosity with `-log-level debug` to log every relayed audio frame.
- **`GET /healthz`** returns `200 ok` whenever the process is answering. Point your uptime
  monitor (UptimeRobot, a cron `curl`, whatever you use) at it.
- **`GET /metrics`** is Prometheus text format. The useful series are
  `chimera_voice_clients`, `chimera_voice_channels`, `chimera_voice_unique_clients_1h`,
  `chimera_voice_packets_dropped_rate_total`, and `chimera_voice_rejected_*_total`. Scrape it, or
  just `watch -n2 curl -s localhost:8081/metrics`.
- **`GET /status`** is a one-line JSON summary, handy for a quick eyeball.

A minimal alert set: `/healthz` down for 2 minutes, and `chimera_voice_clients` stuck at
`max_clients` (the server is full and turning people away — look at
`chimera_voice_rejected_server_full_total`).

## How clients reach it

In the launcher, open the **Voice** tab and enter the relay address under **Relay server**, then
turn on **Use relay server**. The address is `host:port`, e.g. `voice.example.com:47902`. If you
set a password on the server, enter the same one in the launcher; if you set `token_secret`, enter
the secret in **Join token secret** instead. The launcher keeps LAN multicast as a fallback:
turning the relay off, or losing the connection, returns it to the local network.

To ship a default address so users do not have to type one, set `voice.relay.address` in the
build's `local.properties` (gitignored). It becomes `BuildConfig.DEFAULT_VOICE_RELAY_ADDRESS` and
the Voice screen shows it on first run; a value the user saves always overrides it.

## Firewall and NAT notes

- **UDP only.** A relay needs no TCP listener for voice. The game itself may need its own ports;
  this server is independent of it.
- **The server is a NAT traversal endpoint, not a hole puncher.** Clients keep their own NAT
  mappings open with a keepalive beacon every few seconds (the server tells them the interval in
  `HELLO_ACK`). No STUN/TURN is involved; the client always talks to the server, never directly to
  another client.
- **A cloud security group is not the same as a host firewall.** If packets disappear with no log
  line at all, check the cloud firewall first.
- **Test it from outside.** `nc -u voice.example.com 47902` will not get a reply (the server only
  answers valid `CV` packets), so test with the launcher, or with the `voice-relay-probe` snippet
  in the main README's voice section.

## Security

- **Two credential modes.** With `token_secret` set, clients present a short-lived, device-bound
  signed token and never send the secret itself. With only `password` set, the password travels in
  the `HELLO` and should be treated as replayable — for a strong setup restrict the UDP port to
  your players' address ranges, or run the relay on a private network (WireGuard/Tailscale) and
  hand out that address.
- **No authentication of identity.** A client's id is assigned by the server, so a peer cannot
  impersonate another peer's id, but anyone with the credential can join. Do not treat the relay
  as a trusted network for sensitive conversation.
- **Rate limits and caps are the abuse controls.** They are per client and per IP; a flood is
  dropped, counted in `chimera_voice_packets_dropped_rate_total`, and logged at debug level.
- **Bans.** Auto-ban on repeated bad credentials, permanent `banned_ips`/`banned_devices`, and the
  live admin endpoint. A ban drops an existing session immediately.
- **The admin endpoint is protected only by its bearer token.** Bind it to `127.0.0.1` or a
  private interface; never open it to the internet.
- **The server holds no state on disk.** Restarting it drops every session; clients reconnect on
  their own.
