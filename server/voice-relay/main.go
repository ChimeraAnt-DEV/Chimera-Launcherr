// Command voice-relay is the Chimera voice chat relay server.
//
// It forwards audio between clients in the same channel over UDP. It is deliberately small: a
// 1 vCPU / 1 GB VPS is enough for a community, because the server never decodes audio (it moves
// opaque payloads) and never stores state. A client HELLOs, receives an assigned id, then sends
// beacons and audio frames; the server fans each frame out to the peers whose channel can hear
// the sender's, patching the sender's assigned id into the frame so identity cannot be spoofed.
//
// Operationally it exposes /healthz and /metrics over HTTP and writes JSON logs to stderr.
package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"net"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"
)

func main() {
	cfg, err := LoadConfig(os.Args[1:])
	if err != nil {
		if errors.Is(err, flag.ErrHelp) {
			os.Exit(0)
		}
		fmt.Fprintf(os.Stderr, "voice-relay: %v\n", err)
		os.Exit(2)
	}

	logger := NewLogger(cfg.LogLevel)

	// A -mint-token request mints once and exits, so an operator can hand a user a token without
	// running the relay. It needs the token secret, and prints to stdout (not the logger).
	if cfg.MintToken != "" {
		if cfg.TokenSecret == "" {
			fmt.Fprintln(os.Stderr, "voice-relay: -mint-token needs -token-secret (or VOICE_TOKEN_SECRET)")
			os.Exit(2)
		}
		ttl := time.Duration(cfg.TokenTTLHours) * time.Hour
		fmt.Println(IssueToken([]byte(cfg.TokenSecret), cfg.MintToken, ttl, time.Now()))
		return
	}

	metrics := NewMetrics()

	udpAddr, err := net.ResolveUDPAddr("udp", cfg.Listen)
	if err != nil {
		logger.Error("invalid listen address", map[string]any{"listen": cfg.Listen, "err": err.Error()})
		os.Exit(2)
	}
	conn, err := net.ListenUDP("udp", udpAddr)
	if err != nil {
		logger.Error("could not bind the UDP socket", map[string]any{"listen": cfg.Listen, "err": err.Error()})
		os.Exit(1)
	}
	defer conn.Close()

	hub := NewHub(cfg, logger, metrics, conn)

	public := cfg.PublicAddress
	if public == "" {
		public = conn.LocalAddr().String()
	}
	logger.Info("voice relay listening", map[string]any{
		"udp":        conn.LocalAddr().String(),
		"public":     public,
		"http":       cfg.HTTPListen,
		"maxClients": cfg.MaxClients,
		"maxChannel": cfg.MaxChannelMembers,
		"password":   cfg.Password != "",
		"tokenAuth":  cfg.TokenSecret != "",
	})

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	go readLoop(ctx, conn, hub, logger)
	go reaperLoop(ctx, hub)

	var httpServer *http.Server
	if cfg.HTTPListen != "" {
		httpServer = startHTTP(cfg, hub, metrics, logger)
	}

	var adminServer *http.Server
	if cfg.AdminListen != "" {
		adminServer = startAdmin(cfg, hub, logger)
	}

	<-ctx.Done()
	logger.Info("shutting down", nil)
	if httpServer != nil {
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
		_ = httpServer.Shutdown(shutdownCtx)
		cancel()
	}
	if adminServer != nil {
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
		_ = adminServer.Shutdown(shutdownCtx)
		cancel()
	}
	// Closing the socket unblocks the read loop.
	_ = conn.Close()
	// Give the read loop a moment to observe the close before exiting.
	time.Sleep(50 * time.Millisecond)
}

// readLoop is the hot path: read a datagram, hand it to the hub. A read error is only fatal when
// the socket has actually closed; a transient error is logged and the loop continues.
func readLoop(ctx context.Context, conn *net.UDPConn, hub *Hub, logger *Logger) {
	buf := make([]byte, MaxDatagram)
	for {
		n, addr, err := conn.ReadFromUDP(buf)
		if err != nil {
			if ctx.Err() != nil {
				return
			}
			var netErr net.Error
			if errors.As(err, &netErr) && netErr.Temporary() { //nolint:staticcheck // Temporary is the documented signal here
				logger.Debug("transient read error", map[string]any{"err": err.Error()})
				continue
			}
			logger.Error("read failed", map[string]any{"err": err.Error()})
			return
		}
		// The buffer is reused; the hub must not retain it. It copies before fanning out.
		hub.Handle(buf[:n], addr)
	}
}

// reaperLoop evicts clients that went quiet, so a phone that lost signal is removed without a
// BYE. The interval is a fraction of the idle timeout so eviction is prompt without being busy.
func reaperLoop(ctx context.Context, hub *Hub) {
	interval := hub.cfg.IdleTimeout() / 4
	if interval < time.Second {
		interval = time.Second
	}
	ticker := time.NewTicker(interval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			hub.Reap(time.Now())
		}
	}
}

// startHTTP serves the health and metrics endpoints. Health is intentionally trivial: if the
// process is answering, it is up. The useful signal for an operator is in the metrics.
func startHTTP(cfg Config, hub *Hub, metrics *Metrics, logger *Logger) *http.Server {
	mux := http.NewServeMux()
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/plain; charset=utf-8")
		w.WriteHeader(http.StatusOK)
		fmt.Fprintln(w, "ok")
	})
	mux.HandleFunc("/metrics", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
		fmt.Fprint(w, metrics.Render())
	})
	mux.HandleFunc("/status", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json; charset=utf-8")
		clients := hub.Snapshot()
		fmt.Fprintf(w, `{"server":%q,"clients":%d,"channels":%d,"heartbeat_ms":%d}`,
			cfg.ServerName, len(clients), hub.channelCount(), cfg.HeartbeatMs)
		fmt.Fprintln(w)
	})

	server := &http.Server{
		Addr:              cfg.HTTPListen,
		Handler:           mux,
		ReadHeaderTimeout: 5 * time.Second,
	}
	go func() {
		if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			logger.Error("http server stopped", map[string]any{"err": err.Error()})
		}
	}()
	return server
}
