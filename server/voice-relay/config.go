package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

// Config is the whole server configuration. Every field has a safe default and can be set from
// a JSON file, an environment variable, or a flag, in that order of precedence (flag wins).
// A tiny VPS needs only the defaults; the knobs exist for a busier one.
type Config struct {
	// Listen is the UDP bind address for voice traffic.
	Listen string `json:"listen"`
	// HTTPListen is the TCP bind address for /healthz and /metrics. Set to "" to disable.
	HTTPListen string `json:"http_listen"`
	// PublicAddress is advertised to clients in HELLO_ACK so they know what to reconnect to
	// when the bind address is a wildcard.
	PublicAddress string `json:"public_address"`
	// ServerName is shown in the client's status line.
	ServerName string `json:"server_name"`
	// Password, when non-empty, must match the client's HELLO. Empty means open.
	Password string `json:"password"`
	// TokenSecret, when non-empty, enables signed join tokens: a client presents a token
	// derived from this secret instead of the raw password, and the server verifies the
	// signature and expiry. This is the real authentication (see token.go); the password is
	// kept as the simpler fallback for a trusted network.
	TokenSecret string `json:"token_secret"`
	// TokenTTLHours is the default lifetime of a token minted by the -mint-token helper.
	TokenTTLHours int `json:"token_ttl_hours"`

	// AdminListen is the TCP bind address for the operator-only ban/unban endpoint. Empty
	// disables it. It must never be exposed publicly: it is guarded by AdminToken.
	AdminListen string `json:"admin_listen"`
	// AdminToken is the bearer token for the admin endpoint. Required whenever AdminListen is
	// set; the server refuses to start otherwise.
	AdminToken string `json:"admin_token"`

	// BannedIPs are permanent address bans: a plain IP or a CIDR range.
	BannedIPs []string `json:"banned_ips"`
	// BannedDevices are permanent device-id bans, which survive an IP change.
	BannedDevices []string `json:"banned_devices"`
	// AuthFailuresBeforeBan bans an address after this many bad credential attempts within the
	// ban window. 0 disables the auto-ban.
	AuthFailuresBeforeBan int `json:"auth_failures_before_ban"`
	// BanMinutes is both the auto-ban duration and the failure-counting window.
	BanMinutes int `json:"ban_minutes"`

	// HeartbeatMs is the keepalive interval the server asks clients to use. Clients send a
	// beacon (which doubles as a keepalive) at least this often.
	HeartbeatMs int `json:"heartbeat_ms"`
	// IdleTimeoutSec evicts a client that has sent nothing for this long.
	IdleTimeoutSec int `json:"idle_timeout_sec"`

	// MaxClients caps concurrent sessions across the whole server.
	MaxClients int `json:"max_clients"`
	// MaxClientsPerIP caps concurrent sessions from one address, so one host cannot fill the
	// server. 0 disables the per-IP cap.
	MaxClientsPerIP int `json:"max_clients_per_ip"`
	// MaxChannelMembers caps concurrent members per channel. 0 disables the per-channel cap.
	MaxChannelMembers int `json:"max_channel_members"`
	// MaxChannels caps how many distinct channels may exist, so a client cannot create unbounded
	// rooms. 0 disables the cap.
	MaxChannels int `json:"max_channels"`

	// RatePerClientPPS is the per-client token bucket refill (packets per second).
	RatePerClientPPS int `json:"rate_per_client_pps"`
	// RateBurstPackets is the per-client token bucket size.
	RateBurstPackets int `json:"rate_burst_packets"`
	// RatePerClientBPS is the per-client byte budget (bytes per second) for audio.
	RatePerClientBPS int `json:"rate_per_client_bps"`
	// RateBurstBytes is the per-client byte bucket size.
	RateBurstBytes int `json:"rate_burst_bytes"`

	// LogLevel is "debug", "info", or "error".
	LogLevel string `json:"log_level"`

	// MintToken is not persisted config: it carries a -mint-token request out of LoadConfig so
	// main can print a token and exit instead of starting the relay.
	MintToken string `json:"-"`
}

// DefaultConfig returns the shipped defaults: a small, safe relay suitable for one VPS.
func DefaultConfig() Config {
	return Config{
		Listen:                ":47902",
		HTTPListen:            ":8081",
		PublicAddress:         "",
		ServerName:            "Chimera Voice Relay",
		Password:              "",
		TokenSecret:           "",
		TokenTTLHours:         6,
		AdminListen:           "",
		AdminToken:            "",
		AuthFailuresBeforeBan: 5,
		BanMinutes:            15,
		HeartbeatMs:           3000,
		IdleTimeoutSec:        20,
		MaxClients:            200,
		MaxClientsPerIP:       8,
		MaxChannelMembers:     50,
		MaxChannels:           200,
		RatePerClientPPS:      120,
		RateBurstPackets:      240,
		RatePerClientBPS:      32000,
		RateBurstBytes:        64000,
		LogLevel:              "info",
	}
}

// LoadConfig builds a config from defaults, an optional JSON file, environment variables, and
// flags. Missing values fall through, so `voice-relay -password x` works with no file at all.
func LoadConfig(args []string) (Config, error) {
	cfg := DefaultConfig()

	fs := flag.NewFlagSet("voice-relay", flag.ContinueOnError)
	configPath := fs.String("config", "", "path to a JSON config file")
	fs.StringVar(&cfg.Listen, "listen", cfg.Listen, "UDP bind address, e.g. :47902")
	fs.StringVar(&cfg.HTTPListen, "http-listen", cfg.HTTPListen, "HTTP bind address for health/metrics, empty to disable")
	fs.StringVar(&cfg.PublicAddress, "public-address", cfg.PublicAddress, "address advertised to clients, e.g. voice.example.com:47902")
	fs.StringVar(&cfg.ServerName, "server-name", cfg.ServerName, "server name shown to clients")
	fs.StringVar(&cfg.Password, "password", cfg.Password, "shared password; empty means open")
	fs.StringVar(&cfg.TokenSecret, "token-secret", cfg.TokenSecret, "secret for signed join tokens; empty disables token auth")
	fs.IntVar(&cfg.TokenTTLHours, "token-ttl-hours", cfg.TokenTTLHours, "default token lifetime for -mint-token")
	fs.StringVar(&cfg.AdminListen, "admin-listen", cfg.AdminListen, "TCP bind address for the admin ban endpoint, empty to disable")
	fs.StringVar(&cfg.AdminToken, "admin-token", cfg.AdminToken, "bearer token for the admin endpoint")
	fs.IntVar(&cfg.AuthFailuresBeforeBan, "auth-failures-before-ban", cfg.AuthFailuresBeforeBan, "auto-ban an IP after this many bad credential attempts, 0 to disable")
	fs.IntVar(&cfg.BanMinutes, "ban-minutes", cfg.BanMinutes, "auto-ban duration and failure-counting window")
	mintToken := fs.String("mint-token", "", "print a join token for this device id and exit")
	fs.IntVar(&cfg.HeartbeatMs, "heartbeat-ms", cfg.HeartbeatMs, "keepalive interval asked of clients")
	fs.IntVar(&cfg.IdleTimeoutSec, "idle-timeout", cfg.IdleTimeoutSec, "evict a client after this many idle seconds")
	fs.IntVar(&cfg.MaxClients, "max-clients", cfg.MaxClients, "maximum concurrent clients")
	fs.IntVar(&cfg.MaxClientsPerIP, "max-clients-per-ip", cfg.MaxClientsPerIP, "maximum concurrent clients per IP, 0 to disable")
	fs.IntVar(&cfg.MaxChannelMembers, "max-channel-members", cfg.MaxChannelMembers, "maximum members per channel, 0 to disable")
	fs.IntVar(&cfg.MaxChannels, "max-channels", cfg.MaxChannels, "maximum distinct channels, 0 to disable")
	fs.IntVar(&cfg.RatePerClientPPS, "rate-pps", cfg.RatePerClientPPS, "per-client packets per second")
	fs.IntVar(&cfg.RateBurstPackets, "rate-burst-packets", cfg.RateBurstPackets, "per-client packet burst")
	fs.IntVar(&cfg.RatePerClientBPS, "rate-bps", cfg.RatePerClientBPS, "per-client bytes per second")
	fs.IntVar(&cfg.RateBurstBytes, "rate-burst-bytes", cfg.RateBurstBytes, "per-client byte burst")
	fs.StringVar(&cfg.LogLevel, "log-level", cfg.LogLevel, "log level: debug, info, error")

	if err := fs.Parse(args); err != nil {
		return cfg, err
	}

	// A file is applied first so flags and env override it; the file path comes from the flag.
	if *configPath != "" {
		data, err := os.ReadFile(*configPath)
		if err != nil {
			return cfg, fmt.Errorf("read config: %w", err)
		}
		fileCfg := DefaultConfig()
		if err := json.Unmarshal(data, &fileCfg); err != nil {
			return cfg, fmt.Errorf("parse config: %w", err)
		}
		mergeConfig(&cfg, fileCfg, fs)
	}

	applyEnv(&cfg)
	// Re-apply flags last so an explicit flag always wins over a file or the environment.
	if err := fs.Parse(args); err != nil {
		return cfg, err
	}
	cfg.MintToken = *mintToken
	if err := cfg.Validate(); err != nil {
		return cfg, err
	}
	return cfg, nil
}

// mergeConfig copies file values over the defaults, except for fields the user set on the
// command line (which must win).
func mergeConfig(cfg *Config, file Config, fs *flag.FlagSet) {
	set := map[string]bool{}
	fs.Visit(func(f *flag.Flag) { set[f.Name] = true })
	apply := func(name string, dst *string, v string) {
		if !set[name] {
			*dst = v
		}
	}
	apply("listen", &cfg.Listen, file.Listen)
	apply("http-listen", &cfg.HTTPListen, file.HTTPListen)
	apply("public-address", &cfg.PublicAddress, file.PublicAddress)
	apply("server-name", &cfg.ServerName, file.ServerName)
	apply("password", &cfg.Password, file.Password)
	apply("token-secret", &cfg.TokenSecret, file.TokenSecret)
	apply("admin-listen", &cfg.AdminListen, file.AdminListen)
	apply("admin-token", &cfg.AdminToken, file.AdminToken)
	apply("log-level", &cfg.LogLevel, file.LogLevel)
	if !set["token-ttl-hours"] {
		cfg.TokenTTLHours = file.TokenTTLHours
	}
	if !set["auth-failures-before-ban"] {
		cfg.AuthFailuresBeforeBan = file.AuthFailuresBeforeBan
	}
	if !set["ban-minutes"] {
		cfg.BanMinutes = file.BanMinutes
	}
	// A file may carry bans; a flag cannot, so the file value is always taken when present.
	if len(file.BannedIPs) > 0 {
		cfg.BannedIPs = file.BannedIPs
	}
	if len(file.BannedDevices) > 0 {
		cfg.BannedDevices = file.BannedDevices
	}
	if !set["heartbeat-ms"] {
		cfg.HeartbeatMs = file.HeartbeatMs
	}
	if !set["idle-timeout"] {
		cfg.IdleTimeoutSec = file.IdleTimeoutSec
	}
	if !set["max-clients"] {
		cfg.MaxClients = file.MaxClients
	}
	if !set["max-clients-per-ip"] {
		cfg.MaxClientsPerIP = file.MaxClientsPerIP
	}
	if !set["max-channel-members"] {
		cfg.MaxChannelMembers = file.MaxChannelMembers
	}
	if !set["max-channels"] {
		cfg.MaxChannels = file.MaxChannels
	}
	if !set["rate-pps"] {
		cfg.RatePerClientPPS = file.RatePerClientPPS
	}
	if !set["rate-burst-packets"] {
		cfg.RateBurstPackets = file.RateBurstPackets
	}
	if !set["rate-bps"] {
		cfg.RatePerClientBPS = file.RatePerClientBPS
	}
	if !set["rate-burst-bytes"] {
		cfg.RateBurstBytes = file.RateBurstBytes
	}
}

// applyEnv lets a systemd unit or Docker env set the deployment without a config file. Env wins
// over a file but loses to an explicit flag, which is re-parsed after this call.
func applyEnv(cfg *Config) {
	setStr := func(key string, dst *string) {
		if v, ok := os.LookupEnv(key); ok {
			*dst = v
		}
	}
	setInt := func(key string, dst *int) {
		if v, ok := os.LookupEnv(key); ok {
			if n, err := strconv.Atoi(strings.TrimSpace(v)); err == nil {
				*dst = n
			}
		}
	}
	setStr("VOICE_LISTEN", &cfg.Listen)
	setStr("VOICE_HTTP_LISTEN", &cfg.HTTPListen)
	setStr("VOICE_PUBLIC_ADDRESS", &cfg.PublicAddress)
	setStr("VOICE_SERVER_NAME", &cfg.ServerName)
	setStr("VOICE_PASSWORD", &cfg.Password)
	setStr("VOICE_TOKEN_SECRET", &cfg.TokenSecret)
	setStr("VOICE_ADMIN_LISTEN", &cfg.AdminListen)
	setStr("VOICE_ADMIN_TOKEN", &cfg.AdminToken)
	setStr("VOICE_LOG_LEVEL", &cfg.LogLevel)
	setInt("VOICE_TOKEN_TTL_HOURS", &cfg.TokenTTLHours)
	setInt("VOICE_AUTH_FAILURES_BEFORE_BAN", &cfg.AuthFailuresBeforeBan)
	setInt("VOICE_BAN_MINUTES", &cfg.BanMinutes)
	setInt("VOICE_HEARTBEAT_MS", &cfg.HeartbeatMs)
	setInt("VOICE_IDLE_TIMEOUT_SEC", &cfg.IdleTimeoutSec)
	setInt("VOICE_MAX_CLIENTS", &cfg.MaxClients)
	setInt("VOICE_MAX_CLIENTS_PER_IP", &cfg.MaxClientsPerIP)
	setInt("VOICE_MAX_CHANNEL_MEMBERS", &cfg.MaxChannelMembers)
	setInt("VOICE_MAX_CHANNELS", &cfg.MaxChannels)
	setInt("VOICE_RATE_PPS", &cfg.RatePerClientPPS)
	setInt("VOICE_RATE_BURST_PACKETS", &cfg.RateBurstPackets)
	setInt("VOICE_RATE_BPS", &cfg.RatePerClientBPS)
	setInt("VOICE_RATE_BURST_BYTES", &cfg.RateBurstBytes)
}

// Validate clamps nonsense to something operable and returns an error only for a value that
// cannot be made safe (a missing bind address).
func (c *Config) Validate() error {
	if strings.TrimSpace(c.Listen) == "" {
		return fmt.Errorf("listen address must not be empty")
	}
	if c.HeartbeatMs < 500 {
		c.HeartbeatMs = 500
	}
	if c.HeartbeatMs > 60000 {
		c.HeartbeatMs = 60000
	}
	if c.IdleTimeoutSec < 5 {
		c.IdleTimeoutSec = 5
	}
	if c.MaxClients < 1 {
		c.MaxClients = 1
	}
	if c.MaxClientsPerIP < 0 {
		c.MaxClientsPerIP = 0
	}
	if c.MaxChannelMembers < 0 {
		c.MaxChannelMembers = 0
	}
	if c.MaxChannels < 0 {
		c.MaxChannels = 0
	}
	if c.RatePerClientPPS < 10 {
		c.RatePerClientPPS = 10
	}
	if c.RateBurstPackets < c.RatePerClientPPS/2 {
		c.RateBurstPackets = c.RatePerClientPPS
	}
	if c.RatePerClientBPS < 4000 {
		c.RatePerClientBPS = 4000
	}
	if c.RateBurstBytes < c.RatePerClientBPS/2 {
		c.RateBurstBytes = c.RatePerClientBPS
	}
	// The admin endpoint carries bans, so it must be authenticated. Refuse to start rather than
	// silently exposing an unauthenticated control surface.
	if strings.TrimSpace(c.AdminListen) != "" && strings.TrimSpace(c.AdminToken) == "" {
		return fmt.Errorf("admin_listen is set but admin_token is empty; the admin endpoint must be authenticated")
	}
	if c.TokenTTLHours < 1 {
		c.TokenTTLHours = 1
	}
	if c.TokenTTLHours > 24*30 {
		c.TokenTTLHours = 24 * 30
	}
	if c.AuthFailuresBeforeBan < 0 {
		c.AuthFailuresBeforeBan = 0
	}
	if c.BanMinutes < 1 {
		c.BanMinutes = 1
	}
	return nil
}

// BanWindow is the auto-ban duration and the failure-counting window as a duration.
func (c Config) BanWindow() time.Duration {
	return time.Duration(c.BanMinutes) * time.Minute
}

// HeartbeatInterval is the configured keepalive as a duration.
func (c Config) HeartbeatInterval() time.Duration {
	return time.Duration(c.HeartbeatMs) * time.Millisecond
}

// IdleTimeout is the configured idle eviction window as a duration.
func (c Config) IdleTimeout() time.Duration {
	return time.Duration(c.IdleTimeoutSec) * time.Second
}
