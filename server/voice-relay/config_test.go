package main

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func nowForTest() time.Time { return time.Unix(1000, 0) }

func TestDefaultsAreUsable(t *testing.T) {
	cfg, err := LoadConfig(nil)
	if err != nil {
		t.Fatalf("load: %v", err)
	}
	if cfg.Listen == "" || cfg.HeartbeatMs <= 0 || cfg.IdleTimeoutSec <= 0 {
		t.Fatalf("defaults are not operable: %+v", cfg)
	}
}

func TestFlagsOverrideDefaults(t *testing.T) {
	cfg, err := LoadConfig([]string{"-listen", ":5000", "-password", "hunter2", "-max-clients", "7"})
	if err != nil {
		t.Fatalf("load: %v", err)
	}
	if cfg.Listen != ":5000" || cfg.Password != "hunter2" || cfg.MaxClients != 7 {
		t.Fatalf("flags not applied: %+v", cfg)
	}
}

func TestConfigFileThenFlagPrecedence(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "relay.json")
	body := `{"listen":":6000","password":"fromfile","max_clients":12,"server_name":"File Relay"}`
	if err := os.WriteFile(path, []byte(body), 0o600); err != nil {
		t.Fatal(err)
	}

	cfg, err := LoadConfig([]string{"-config", path})
	if err != nil {
		t.Fatalf("load: %v", err)
	}
	if cfg.Listen != ":6000" || cfg.Password != "fromfile" || cfg.MaxClients != 12 {
		t.Fatalf("file values not applied: %+v", cfg)
	}

	// A flag must beat the file for the same key, and the file must still supply the rest.
	cfg, err = LoadConfig([]string{"-config", path, "-password", "flagwins"})
	if err != nil {
		t.Fatalf("load: %v", err)
	}
	if cfg.Password != "flagwins" {
		t.Fatalf("flag did not override the file: %q", cfg.Password)
	}
	if cfg.Listen != ":6000" || cfg.MaxClients != 12 {
		t.Fatalf("file values lost when a flag was present: %+v", cfg)
	}
}

func TestEnvIsApplied(t *testing.T) {
	t.Setenv("VOICE_LISTEN", ":7000")
	t.Setenv("VOICE_PASSWORD", "envpass")
	t.Setenv("VOICE_MAX_CLIENTS", "3")
	cfg, err := LoadConfig(nil)
	if err != nil {
		t.Fatalf("load: %v", err)
	}
	if cfg.Listen != ":7000" || cfg.Password != "envpass" || cfg.MaxClients != 3 {
		t.Fatalf("env not applied: %+v", cfg)
	}
}

func TestValidateClampsNonsense(t *testing.T) {
	cfg := DefaultConfig()
	cfg.HeartbeatMs = 1
	cfg.IdleTimeoutSec = 0
	cfg.MaxClients = -5
	cfg.RatePerClientPPS = 0
	cfg.RatePerClientBPS = 0
	if err := cfg.Validate(); err != nil {
		t.Fatalf("validate: %v", err)
	}
	if cfg.HeartbeatMs < 500 {
		t.Fatalf("heartbeat not clamped: %d", cfg.HeartbeatMs)
	}
	if cfg.IdleTimeoutSec < 5 {
		t.Fatalf("idle timeout not clamped: %d", cfg.IdleTimeoutSec)
	}
	if cfg.MaxClients < 1 {
		t.Fatalf("max clients not clamped: %d", cfg.MaxClients)
	}
	if cfg.RatePerClientPPS < 10 || cfg.RatePerClientBPS < 4000 {
		t.Fatalf("rates not clamped: %+v", cfg)
	}
}

func TestEmptyListenIsAnError(t *testing.T) {
	cfg := DefaultConfig()
	cfg.Listen = ""
	if err := cfg.Validate(); err == nil {
		t.Fatal("an empty listen address must be rejected")
	}
}

func TestRateLimiterByteBudget(t *testing.T) {
	now := nowForTest()
	lim := NewRateLimiter(1000, 1000, 1000, 2000, now)
	// A packet larger than the bucket is refused outright.
	if lim.Allow(5000, now) {
		t.Fatal("an oversized packet must be refused")
	}
	// Fill the byte bucket, then it must refuse.
	allowed := 0
	for i := 0; i < 20; i++ {
		if lim.Allow(500, now) {
			allowed++
		}
	}
	if allowed != 4 {
		t.Fatalf("byte bucket allowed %d packets of 500 into 2000 bytes", allowed)
	}
}

func TestRateLimiterRefillsOverTime(t *testing.T) {
	now := nowForTest()
	lim := NewRateLimiter(10, 2, 100000, 100000, now)
	if !lim.Allow(10, now) || !lim.Allow(10, now) {
		t.Fatal("initial burst should pass")
	}
	if lim.Allow(10, now) {
		t.Fatal("burst is exhausted, next should fail")
	}
	// After half a second at 10 pps, five tokens are back.
	if !lim.Allow(10, now.Add(500*1000*1000)) { // 500ms in ns
		t.Fatal("tokens should have refilled")
	}
}

func TestMetricsRenderIsPrometheusShaped(t *testing.T) {
	m := NewMetrics()
	m.clients.Store(3)
	m.packetsIn.Add(10)
	out := m.Render()
	for _, want := range []string{
		"chimera_voice_clients 3",
		"chimera_voice_packets_in_total 10",
		"# TYPE chimera_voice_clients gauge",
	} {
		if !strings.Contains(out, want) {
			t.Fatalf("metrics output missing %q:\n%s", want, out)
		}
	}
}
