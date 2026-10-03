package main

import (
	"testing"
	"time"
)

// helloBytes builds a v4 HELLO with a device id and a credential payload.
func helloBytes(name, device, channel string, credential []byte) []byte {
	return build(Packet{
		Type: TypeHello, PeerID: "", Name: name, DeviceID: device, Channel: channel,
		Payload: credential,
	})
}

// lastSent returns the last datagram the fake conn received for addr.
func lastSent(t *testing.T, conn *fakeConn, addr interface{ String() string }) Packet {
	t.Helper()
	var data []byte
	for _, s := range conn.sent {
		if s.addr.String() == addr.String() {
			data = s.data
		}
	}
	if data == nil {
		t.Fatal("hub sent nothing")
	}
	p, err := ParsePacket(data)
	if err != nil {
		t.Fatalf("last datagram did not parse: %v", err)
	}
	return p
}

func TestTokenAuthAdmitsAValidToken(t *testing.T) {
	secret := "unit-secret"
	cfg := DefaultConfig()
	cfg.TokenSecret = secret
	conn := &fakeConn{}
	hub := NewHub(cfg, NewLogger("error"), NewMetrics(), conn)
	now := time.Now()
	hub.now = func() time.Time { return now }

	addr := testAddr(1000)
	token := IssueToken([]byte(secret), "dev-a", time.Hour, now)
	hub.Handle(helloBytes("Phone", "dev-a", "world", []byte(token)), addr)

	p := lastSent(t, conn, addr)
	if p.Type != TypeHelloAck {
		t.Fatalf("expected HELLO_ACK, got %+v", p)
	}
	if p.ClientID == 0 {
		t.Error("no client id assigned")
	}
}

func TestTokenAuthRejectsBadToken(t *testing.T) {
	cfg := DefaultConfig()
	cfg.TokenSecret = "unit-secret"
	conn := &fakeConn{}
	hub := NewHub(cfg, NewLogger("error"), NewMetrics(), conn)
	hub.now = func() time.Time { return time.Now() }

	addr := testAddr(1001)
	hub.Handle(helloBytes("Phone", "dev-a", "world", []byte("not-a-token")), addr)

	p := lastSent(t, conn, addr)
	if p.Type != TypeNotice || p.Sequence != NoticeBadToken {
		t.Fatalf("expected a BAD_TOKEN notice, got %+v", p)
	}
}

func TestTokenAuthRejectsDeviceMismatch(t *testing.T) {
	secret := "s"
	cfg := DefaultConfig()
	cfg.TokenSecret = secret
	conn := &fakeConn{}
	hub := NewHub(cfg, NewLogger("error"), NewMetrics(), conn)
	now := time.Now()
	hub.now = func() time.Time { return now }

	// Token issued for dev-a, presented by dev-b.
	addr := testAddr(1002)
	token := IssueToken([]byte(secret), "dev-a", time.Hour, now)
	hub.Handle(helloBytes("Phone", "dev-b", "world", []byte(token)), addr)

	p := lastSent(t, conn, addr)
	if p.Type != TypeNotice || p.Sequence != NoticeBadToken {
		t.Fatalf("expected a BAD_TOKEN notice, got %+v", p)
	}
}

func TestPasswordFallbackStillWorks(t *testing.T) {
	cfg := DefaultConfig()
	cfg.Password = "hunter2"
	conn := &fakeConn{}
	hub := NewHub(cfg, NewLogger("error"), NewMetrics(), conn)
	now := time.Now()
	hub.now = func() time.Time { return now }

	addr := testAddr(1003)
	hub.Handle(helloBytes("Phone", "dev-a", "world", []byte("hunter2")), addr)
	if p := lastSent(t, conn, addr); p.Type != TypeHelloAck {
		t.Fatalf("right password refused: %+v", p)
	}

	conn.reset()
	addr2 := testAddr(1004)
	hub.Handle(helloBytes("Phone", "dev-a", "world", []byte("wrong")), addr2)
	if p := lastSent(t, conn, addr2); p.Type != TypeNotice || p.Sequence != NoticeBadPassword {
		t.Fatalf("wrong password not refused: %+v", p)
	}
}

func TestBannedDeviceIsRefusedEvenWithAValidToken(t *testing.T) {
	secret := "s"
	cfg := DefaultConfig()
	cfg.TokenSecret = secret
	cfg.BannedDevices = []string{"dev-bad"}
	conn := &fakeConn{}
	hub := NewHub(cfg, NewLogger("error"), NewMetrics(), conn)
	now := time.Now()
	hub.now = func() time.Time { return now }

	addr := testAddr(1005)
	token := IssueToken([]byte(secret), "dev-bad", time.Hour, now)
	hub.Handle(helloBytes("Phone", "dev-bad", "world", []byte(token)), addr)

	p := lastSent(t, conn, addr)
	if p.Type != TypeNotice || p.Sequence != NoticeBanned {
		t.Fatalf("expected a BANNED notice, got %+v", p)
	}
}

func TestAutoBanClosesTheDoor(t *testing.T) {
	cfg := DefaultConfig()
	cfg.Password = "right"
	cfg.AuthFailuresBeforeBan = 3
	cfg.BanMinutes = 10
	conn := &fakeConn{}
	hub := NewHub(cfg, NewLogger("error"), NewMetrics(), conn)
	now := time.Now()
	hub.now = func() time.Time { return now }

	failing := testAddr(1006)
	for i := 0; i < 3; i++ {
		hub.Handle(helloBytes("Phone", "d", "world", []byte("wrong")), failing)
	}
	// Now even the right password must be refused: the address is banned.
	conn.reset()
	hub.Handle(helloBytes("Phone", "d", "world", []byte("right")), failing)
	p := lastSent(t, conn, failing)
	if p.Type != TypeNotice || p.Sequence != NoticeBanned {
		t.Fatalf("expected a BANNED notice after auto-ban, got %+v", p)
	}
}

func TestDisconnectBannedDropsLiveSessions(t *testing.T) {
	cfg := DefaultConfig()
	conn := &fakeConn{}
	hub := NewHub(cfg, NewLogger("error"), NewMetrics(), conn)
	now := time.Now()
	hub.now = func() time.Time { return now }

	addr := testAddr(1008)
	hub.Handle(helloBytes("Phone", "dev-x", "world", nil), addr)
	if len(hub.Snapshot()) != 1 {
		t.Fatalf("session not admitted")
	}

	hub.Bans().BanFor(addr.IP.String(), time.Minute, now)
	if dropped := hub.DisconnectBanned(now); dropped != 1 {
		t.Errorf("dropped = %d, want 1", dropped)
	}
	if len(hub.Snapshot()) != 0 {
		t.Error("banned session still present")
	}
}

func TestOpenRelayStillAdmitsWithoutCredentials(t *testing.T) {
	cfg := DefaultConfig() // no password, no token
	conn := &fakeConn{}
	hub := NewHub(cfg, NewLogger("error"), NewMetrics(), conn)
	hub.now = func() time.Time { return time.Now() }

	addr := testAddr(1009)
	hub.Handle(helloBytes("Phone", "", "world", nil), addr)
	if p := lastSent(t, conn, addr); p.Type != TypeHelloAck {
		t.Fatalf("open relay refused a client: %+v", p)
	}
}
