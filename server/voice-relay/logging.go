package main

import (
	"encoding/json"
	"fmt"
	"log"
	"os"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

// logLevel orders the three levels the server uses.
type logLevel int

const (
	levelDebug logLevel = iota
	levelInfo
	levelError
)

func parseLevel(s string) logLevel {
	switch strings.ToLower(strings.TrimSpace(s)) {
	case "debug":
		return levelDebug
	case "error":
		return levelError
	default:
		return levelInfo
	}
}

// Logger is a tiny JSON-lines logger. Structured lines are what makes the server greppable by
// journalctl, Docker, or Loki without a parsing pipeline: every line carries a level, a message,
// and whatever fields the call site adds.
type Logger struct {
	level logLevel
	out   *log.Logger
}

// NewLogger builds a logger writing to stderr (where systemd and Docker collect it).
func NewLogger(level string) *Logger {
	return &Logger{level: parseLevel(level), out: log.New(os.Stderr, "", 0)}
}

func (l *Logger) log(lv logLevel, name, msg string, fields map[string]any) {
	if lv < l.level {
		return
	}
	entry := make(map[string]any, len(fields)+3)
	entry["ts"] = time.Now().UTC().Format(time.RFC3339)
	entry["level"] = name
	entry["msg"] = msg
	for k, v := range fields {
		entry[k] = v
	}
	line, err := json.Marshal(entry)
	if err != nil {
		return
	}
	l.out.Println(string(line))
}

func (l *Logger) Debug(msg string, fields map[string]any) { l.log(levelDebug, "debug", msg, fields) }
func (l *Logger) Info(msg string, fields map[string]any)  { l.log(levelInfo, "info", msg, fields) }
func (l *Logger) Error(msg string, fields map[string]any) { l.log(levelError, "error", msg, fields) }

// Metrics counts what an operator watches: who is connected, how much traffic flows, and what
// the server refused. The HTTP /metrics endpoint renders these in Prometheus text format.
type Metrics struct {
	started time.Time

	clients            atomic.Int64
	channels           atomic.Int64
	packetsIn          atomic.Int64
	packetsOut         atomic.Int64
	bytesIn            atomic.Int64
	bytesOut           atomic.Int64
	packetsDroppedRate atomic.Int64
	packetsDroppedBad  atomic.Int64
	sessionsTotal      atomic.Int64
	rejectedFull       atomic.Int64
	rejectedPassword   atomic.Int64
	rejectedChannel    atomic.Int64
	rejectedAuth       atomic.Int64
	rejectedBanned     atomic.Int64
	bansApplied        atomic.Int64

	mu       sync.Mutex
	lastSeen map[uint64]time.Time
}

func NewMetrics() *Metrics {
	return &Metrics{started: time.Now(), lastSeen: map[uint64]time.Time{}}
}

// noteClient records that a client id was seen, for the "unique clients recently" gauge.
func (m *Metrics) noteClient(id uint64, now time.Time) {
	m.mu.Lock()
	m.lastSeen[id] = now
	if len(m.lastSeen) > 4096 {
		cutoff := now.Add(-time.Hour)
		for k, t := range m.lastSeen {
			if t.Before(cutoff) {
				delete(m.lastSeen, k)
			}
		}
	}
	m.mu.Unlock()
}

// uniqueClients returns how many distinct ids were seen within the window.
func (m *Metrics) uniqueClients(now time.Time, window time.Duration) int {
	m.mu.Lock()
	defer m.mu.Unlock()
	cutoff := now.Add(-window)
	count := 0
	for _, t := range m.lastSeen {
		if t.After(cutoff) {
			count++
		}
	}
	return count
}

// Render writes the Prometheus text exposition format. Only counters and gauges are used; a
// scraper does not need histograms to answer "is it up and is anyone on it".
func (m *Metrics) Render() string {
	var b strings.Builder
	writeGauge := func(name, help string, value int64) {
		fmt.Fprintf(&b, "# HELP %s %s\n# TYPE %s gauge\n%s %d\n", name, help, name, name, value)
	}
	writeCounter := func(name, help string, value int64) {
		fmt.Fprintf(&b, "# HELP %s %s\n# TYPE %s counter\n%s %d\n", name, help, name, name, value)
	}

	writeGauge("chimera_voice_clients", "Current connected clients", m.clients.Load())
	writeGauge("chimera_voice_channels", "Current active channels", m.channels.Load())
	writeGauge("chimera_voice_unique_clients_1h", "Distinct client ids seen in the last hour",
		int64(m.uniqueClients(time.Now(), time.Hour)))
	writeGauge("chimera_voice_uptime_seconds", "Seconds since the server started",
		int64(time.Since(m.started).Seconds()))
	writeCounter("chimera_voice_packets_in_total", "Datagrams received", m.packetsIn.Load())
	writeCounter("chimera_voice_packets_out_total", "Datagrams relayed", m.packetsOut.Load())
	writeCounter("chimera_voice_bytes_in_total", "Bytes received", m.bytesIn.Load())
	writeCounter("chimera_voice_bytes_out_total", "Bytes relayed", m.bytesOut.Load())
	writeCounter("chimera_voice_packets_dropped_rate_total", "Datagrams dropped by the rate limiter",
		m.packetsDroppedRate.Load())
	writeCounter("chimera_voice_packets_dropped_malformed_total", "Datagrams dropped as malformed",
		m.packetsDroppedBad.Load())
	writeCounter("chimera_voice_sessions_total", "Sessions accepted since start", m.sessionsTotal.Load())
	writeCounter("chimera_voice_rejected_server_full_total", "HELLOs refused because the server was full",
		m.rejectedFull.Load())
	writeCounter("chimera_voice_rejected_password_total", "HELLOs refused for a bad password",
		m.rejectedPassword.Load())
	writeCounter("chimera_voice_rejected_channel_full_total", "Joins refused because the channel was full",
		m.rejectedChannel.Load())
	writeCounter("chimera_voice_rejected_auth_total", "HELLOs refused for a bad credential",
		m.rejectedAuth.Load())
	writeCounter("chimera_voice_rejected_banned_total", "HELLOs refused because the client is banned",
		m.rejectedBanned.Load())
	writeCounter("chimera_voice_bans_applied_total", "Addresses auto-banned after repeated auth failures",
		m.bansApplied.Load())
	return b.String()
}
