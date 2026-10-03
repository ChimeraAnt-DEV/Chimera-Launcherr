package main

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

// TestAdminRequiresAToken proves the ban endpoint is not an open control surface.
func TestAdminRequiresAToken(t *testing.T) {
	cfg := DefaultConfig()
	cfg.AdminListen = "127.0.0.1:0"
	cfg.AdminToken = "s3cret"
	hub := NewHub(cfg, NewLogger("error"), NewMetrics(), &fakeConn{})
	server := startAdmin(cfg, hub, NewLogger("error"))
	defer server.Close()

	// No token.
	req := httptest.NewRequest(http.MethodPost, "/admin/ban", bytes.NewBufferString(`{"ip":"1.2.3.4"}`))
	rec := httptest.NewRecorder()
	handlerFor(server, "/admin/ban").ServeHTTP(rec, req)
	if rec.Code != http.StatusUnauthorized {
		t.Errorf("no token: status = %d, want 401", rec.Code)
	}

	// Wrong token.
	req = httptest.NewRequest(http.MethodPost, "/admin/ban", bytes.NewBufferString(`{"ip":"1.2.3.4"}`))
	req.Header.Set("Authorization", "Bearer nope")
	rec = httptest.NewRecorder()
	handlerFor(server, "/admin/ban").ServeHTTP(rec, req)
	if rec.Code != http.StatusUnauthorized {
		t.Errorf("wrong token: status = %d, want 401", rec.Code)
	}
}

// TestAdminBanAndUnban moves an address in and out of the ban list.
func TestAdminBanAndUnban(t *testing.T) {
	cfg := DefaultConfig()
	cfg.AdminListen = "127.0.0.1:0"
	cfg.AdminToken = "s3cret"
	hub := NewHub(cfg, NewLogger("error"), NewMetrics(), &fakeConn{})
	server := startAdmin(cfg, hub, NewLogger("error"))
	defer server.Close()
	handler := handlerFor(server, "/admin/ban")

	body := func(payload string) *httptest.ResponseRecorder {
		req := httptest.NewRequest(http.MethodPost, "/admin/ban", bytes.NewBufferString(payload))
		req.Header.Set("Authorization", "Bearer s3cret")
		rec := httptest.NewRecorder()
		handler.ServeHTTP(rec, req)
		return rec
	}

	if rec := body(`{"ip":"203.0.113.77","minutes":5}`); rec.Code != http.StatusOK {
		t.Fatalf("ban status = %d", rec.Code)
	}
	if !hub.Bans().Banned("203.0.113.77", "", time.Now()) {
		t.Error("ip not banned via the admin endpoint")
	}

	unban := handlerFor(server, "/admin/unban")
	req := httptest.NewRequest(http.MethodPost, "/admin/unban", bytes.NewBufferString(`{"ip":"203.0.113.77"}`))
	req.Header.Set("Authorization", "Bearer s3cret")
	rec := httptest.NewRecorder()
	unban.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("unban status = %d", rec.Code)
	}
	if hub.Bans().Banned("203.0.113.77", "", time.Now()) {
		t.Error("ip still banned after unban")
	}
}

// TestAdminBanDisconnectsLiveSession checks a ban takes effect immediately, not at the idle timeout.
func TestAdminBanDisconnectsLiveSession(t *testing.T) {
	cfg := DefaultConfig()
	cfg.AdminListen = "127.0.0.1:0"
	cfg.AdminToken = "s3cret"
	hub := NewHub(cfg, NewLogger("error"), NewMetrics(), &fakeConn{})
	now := time.Now()
	hub.now = func() time.Time { return now }

	addr := testAddr(2000)
	hub.Handle(helloBytes("Phone", "dev", "world", nil), addr)
	if len(hub.Snapshot()) != 1 {
		t.Fatal("session not admitted")
	}

	server := startAdmin(cfg, hub, NewLogger("error"))
	defer server.Close()
	handler := handlerFor(server, "/admin/ban")
	req := httptest.NewRequest(http.MethodPost, "/admin/ban",
		bytes.NewBufferString(`{"device":"dev"}`))
	req.Header.Set("Authorization", "Bearer s3cret")
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)

	var resp adminResponse
	_ = json.Unmarshal(rec.Body.Bytes(), &resp)
	if resp.Disconnected != 1 {
		t.Errorf("disconnected = %d, want 1", resp.Disconnected)
	}
	if len(hub.Snapshot()) != 0 {
		t.Error("banned session still present")
	}
}

// handlerFor finds the handler registered for a path on the server's mux.
func handlerFor(server *http.Server, path string) http.Handler {
	mux, ok := server.Handler.(*http.ServeMux)
	if !ok {
		panic("admin server handler is not a ServeMux")
	}
	handler, _ := mux.Handler(httptest.NewRequest(http.MethodPost, path, nil))
	return handler
}
