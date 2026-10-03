package main

import (
	"crypto/subtle"
	"encoding/json"
	"net/http"
	"strings"
	"time"
)

// The admin endpoint is the operator's control surface for bans. It is deliberately a separate
// listener from /metrics: bans are a write action, so they must not share a port that is often
// exposed to a monitoring host. It is guarded by a bearer token, and the server refuses to start
// with the endpoint enabled and no token (see Config.Validate).
//
// Endpoints:
//
//	GET  /admin/bans            list the live temporary bans and the static entries
//	POST /admin/ban             {"ip":"1.2.3.4","minutes":60} or {"device":"abc","minutes":60}
//	POST /admin/unban           {"ip":"1.2.3.4"} or {"device":"abc"}
//
// A device ban is the durable one: it survives an IP change. An IP ban is the fast one: it stops
// an address immediately, including any live session on it.

type adminRequest struct {
	IP      string `json:"ip"`
	Device  string `json:"device"`
	Minutes int    `json:"minutes"`
}

type adminResponse struct {
	OK            bool     `json:"ok"`
	Error         string   `json:"error,omitempty"`
	TemporaryBans int      `json:"temporary_bans"`
	Disconnected  int      `json:"disconnected"`
	BannedIPs     []string `json:"banned_ips,omitempty"`
	BannedDevices []string `json:"banned_devices,omitempty"`
}

// startAdmin serves the ban endpoint on cfg.AdminListen. It returns the server so main can shut it
// down. The endpoint is only started when AdminListen is set; Validate has already ensured a token.
func startAdmin(cfg Config, hub *Hub, logger *Logger) *http.Server {
	mux := http.NewServeMux()

	authorized := func(r *http.Request) bool {
		header := r.Header.Get("Authorization")
		const prefix = "Bearer "
		if !strings.HasPrefix(header, prefix) {
			return false
		}
		presented := strings.TrimSpace(strings.TrimPrefix(header, prefix))
		// Constant-time compare so the token cannot be guessed a byte at a time.
		return subtle.ConstantTimeCompare([]byte(presented), []byte(cfg.AdminToken)) == 1
	}

	mux.HandleFunc("/admin/bans", func(w http.ResponseWriter, r *http.Request) {
		if !authorized(r) {
			http.Error(w, "unauthorized", http.StatusUnauthorized)
			return
		}
		now := time.Now()
		writeJSON(w, http.StatusOK, adminResponse{
			OK:            true,
			TemporaryBans: hub.Bans().TempCount(now),
			BannedIPs:     cfg.BannedIPs,
			BannedDevices: cfg.BannedDevices,
		})
	})

	mux.HandleFunc("/admin/ban", func(w http.ResponseWriter, r *http.Request) {
		if !authorized(r) {
			http.Error(w, "unauthorized", http.StatusUnauthorized)
			return
		}
		if r.Method != http.MethodPost {
			http.Error(w, "POST required", http.StatusMethodNotAllowed)
			return
		}
		var req adminRequest
		if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 4096)).Decode(&req); err != nil {
			writeJSON(w, http.StatusBadRequest, adminResponse{Error: "invalid JSON body"})
			return
		}
		minutes := req.Minutes
		if minutes <= 0 {
			minutes = cfg.BanMinutes
		}
		now := time.Now()
		if req.IP != "" {
			hub.Bans().BanFor(req.IP, time.Duration(minutes)*time.Minute, now)
		}
		if req.Device != "" {
			hub.Bans().AddDevice(req.Device)
		}
		if req.IP == "" && req.Device == "" {
			writeJSON(w, http.StatusBadRequest, adminResponse{Error: "ip or device required"})
			return
		}
		dropped := hub.DisconnectBanned(now)
		logger.Info("admin ban applied", map[string]any{
			"ip": req.IP, "device": req.Device, "minutes": minutes, "disconnected": dropped,
		})
		writeJSON(w, http.StatusOK, adminResponse{
			OK: true, Disconnected: dropped, TemporaryBans: hub.Bans().TempCount(now),
		})
	})

	mux.HandleFunc("/admin/unban", func(w http.ResponseWriter, r *http.Request) {
		if !authorized(r) {
			http.Error(w, "unauthorized", http.StatusUnauthorized)
			return
		}
		if r.Method != http.MethodPost {
			http.Error(w, "POST required", http.StatusMethodNotAllowed)
			return
		}
		var req adminRequest
		if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 4096)).Decode(&req); err != nil {
			writeJSON(w, http.StatusBadRequest, adminResponse{Error: "invalid JSON body"})
			return
		}
		if req.IP == "" && req.Device == "" {
			writeJSON(w, http.StatusBadRequest, adminResponse{Error: "ip or device required"})
			return
		}
		if req.IP != "" {
			hub.Bans().RemoveTemp(req.IP)
		}
		if req.Device != "" {
			hub.Bans().RemoveDevice(req.Device)
		}
		logger.Info("admin unban applied", map[string]any{"ip": req.IP, "device": req.Device})
		writeJSON(w, http.StatusOK, adminResponse{
			OK: true, TemporaryBans: hub.Bans().TempCount(time.Now()),
		})
	})

	server := &http.Server{
		Addr:              cfg.AdminListen,
		Handler:           mux,
		ReadHeaderTimeout: 5 * time.Second,
	}
	go func() {
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			logger.Error("admin server stopped", map[string]any{"err": err.Error()})
		}
	}()
	return server
}

func writeJSON(w http.ResponseWriter, status int, body adminResponse) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}
