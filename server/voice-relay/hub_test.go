package main

import (
	"net"
	"testing"
	"time"
)

// fakeConn captures datagrams the hub sends, so the fan-out rule can be asserted without a socket.
type fakeConn struct {
	sent []sentDatagram
}

type sentDatagram struct {
	data []byte
	addr *net.UDPAddr
}

func (f *fakeConn) WriteToUDP(b []byte, addr *net.UDPAddr) (int, error) {
	cp := make([]byte, len(b))
	copy(cp, b)
	f.sent = append(f.sent, sentDatagram{data: cp, addr: addr})
	return len(b), nil
}

func (f *fakeConn) to(addr *net.UDPAddr) [][]byte {
	var out [][]byte
	for _, s := range f.sent {
		if s.addr.String() == addr.String() {
			out = append(out, s.data)
		}
	}
	return out
}

func (f *fakeConn) reset() { f.sent = nil }

func testAddr(port int) *net.UDPAddr {
	return &net.UDPAddr{IP: net.IPv4(10, 0, 0, byte(port)), Port: port}
}

// newTestHub builds a hub with a fake clock so idle eviction is deterministic.
func newTestHub(t *testing.T, mutate func(*Config)) (*Hub, *fakeConn) {
	t.Helper()
	cfg := DefaultConfig()
	if mutate != nil {
		mutate(&cfg)
	}
	conn := &fakeConn{}
	hub := NewHub(cfg, NewLogger("error"), NewMetrics(), conn)
	now := time.Unix(1000, 0)
	hub.now = func() time.Time { return now }
	return hub, conn
}

// hello admits a client at the given address and returns the assigned id.
func hello(t *testing.T, hub *Hub, conn *fakeConn, addr *net.UDPAddr, name, channel string) uint64 {
	t.Helper()
	conn.reset()
	hub.Handle(build(Packet{Type: TypeHello, Name: name, Channel: channel}), addr)
	acks := conn.to(addr)
	if len(acks) == 0 {
		t.Fatalf("no HELLO_ACK for %s", addr)
	}
	p, err := ParsePacket(acks[len(acks)-1])
	if err != nil {
		t.Fatalf("ack parse: %v", err)
	}
	if p.Type != TypeHelloAck {
		t.Fatalf("expected HELLO_ACK, got type %d", p.Type)
	}
	return p.ClientID
}

func TestHelloAssignsAUniqueIDAndAcks(t *testing.T) {
	hub, conn := newTestHub(t, nil)
	a := testAddr(1)
	b := testAddr(2)
	idA := hello(t, hub, conn, a, "A", "world")
	idB := hello(t, hub, conn, b, "B", "world")
	if idA == 0 || idB == 0 {
		t.Fatal("assigned ids must be non-zero")
	}
	if idA == idB {
		t.Fatalf("ids must be unique, both %d", idA)
	}
}

func TestARecurringHelloReplacesTheSessionInsteadOfAccumulating(t *testing.T) {
	hub, conn := newTestHub(t, func(c *Config) { c.MaxClients = 2 })
	addr := testAddr(1)
	hello(t, hub, conn, addr, "A", "world")
	hello(t, hub, conn, addr, "A", "world")
	hello(t, hub, conn, addr, "A", "world")
	if got := len(hub.Snapshot()); got != 1 {
		t.Fatalf("sessions = %d, want 1 (a re-hello must replace)", got)
	}
}

func TestAudioFansOutOnlyToHearersAndCarriesTheSenderID(t *testing.T) {
	hub, conn := newTestHub(t, nil)
	a := testAddr(1)
	b := testAddr(2)
	c := testAddr(3)
	idA := hello(t, hub, conn, a, "A", "world")
	hello(t, hub, conn, b, "B", "team")
	hello(t, hub, conn, c, "C", "squad")

	conn.reset()
	// A is on the open channel, which reaches across in both directions, so both B and C hear it.
	// The point of the test is that the relayed frame carries A's *assigned* id, not the id A
	// claimed in the packet (12345).
	hub.Handle(build(Packet{Type: TypeAudio, ClientID: 12345, Name: "A", Channel: "world",
		Codec: CodecOpus, Payload: []byte{1, 2, 3}}), a)

	for _, addr := range []*net.UDPAddr{b, c} {
		got := conn.to(addr)
		if len(got) != 1 {
			t.Fatalf("%s received %d datagrams, want 1", addr, len(got))
		}
		p, err := ParsePacket(got[0])
		if err != nil {
			t.Fatalf("relayed parse: %v", err)
		}
		if p.ClientID != idA {
			t.Fatalf("relayed id = %d, want the assigned %d (spoofed 12345 must be replaced)",
				p.ClientID, idA)
		}
	}
	if got := conn.to(a); len(got) != 0 {
		t.Fatalf("sender must not receive its own audio, got %d", len(got))
	}
}

func TestTwoPrivateChannelsDoNotHearEachOther(t *testing.T) {
	hub, conn := newTestHub(t, nil)
	a := testAddr(1)
	b := testAddr(2)
	hello(t, hub, conn, a, "A", "GLOWBERRY-AAAA")
	hello(t, hub, conn, b, "B", "GLOWBERRY-BBBB")

	conn.reset()
	hub.Handle(build(Packet{Type: TypeAudio, ClientID: 1, Name: "A",
		Channel: "GLOWBERRY-AAAA", Payload: []byte{1}}), a)
	if got := conn.to(b); len(got) != 0 {
		t.Fatalf("B must not hear a different private channel, got %d datagrams", len(got))
	}
}

func TestByeRemovesTheSession(t *testing.T) {
	hub, conn := newTestHub(t, nil)
	a := testAddr(1)
	hello(t, hub, conn, a, "A", "world")
	if len(hub.Snapshot()) != 1 {
		t.Fatal("expected one session")
	}
	hub.Handle(build(Packet{Type: TypeBye, Channel: "world"}), a)
	if len(hub.Snapshot()) != 0 {
		t.Fatal("BYE must remove the session")
	}
}

func TestServerFullIsRefused(t *testing.T) {
	hub, conn := newTestHub(t, func(c *Config) { c.MaxClients = 1; c.MaxClientsPerIP = 0 })
	hello(t, hub, conn, testAddr(1), "A", "world")
	conn.reset()
	addr := testAddr(2)
	hub.Handle(build(Packet{Type: TypeHello, Name: "B", Channel: "world"}), addr)
	got := conn.to(addr)
	if len(got) != 1 {
		t.Fatalf("expected one reply, got %d", len(got))
	}
	p, _ := ParsePacket(got[0])
	if p.Type != TypeNotice || p.Sequence != NoticeServerFull {
		t.Fatalf("expected SERVER_FULL notice, got %+v", p)
	}
}

func TestWrongPasswordIsRefused(t *testing.T) {
	hub, conn := newTestHub(t, func(c *Config) { c.Password = "secret" })
	addr := testAddr(1)
	conn.reset()
	hub.Handle(build(Packet{Type: TypeHello, Name: "A", Channel: "world", Payload: []byte("nope")}), addr)
	p, _ := ParsePacket(conn.to(addr)[0])
	if p.Type != TypeNotice || p.Sequence != NoticeBadPassword {
		t.Fatalf("expected BAD_PASSWORD, got %+v", p)
	}
	conn.reset()
	hub.Handle(build(Packet{Type: TypeHello, Name: "A", Channel: "world", Payload: []byte("secret")}), addr)
	p, _ = ParsePacket(conn.to(addr)[0])
	if p.Type != TypeHelloAck {
		t.Fatalf("correct password must be admitted, got %+v", p)
	}
}

func TestPerChannelCapRefusesAJoinButKeepsTheOldChannel(t *testing.T) {
	hub, conn := newTestHub(t, func(c *Config) {
		c.MaxChannelMembers = 1
		c.MaxClientsPerIP = 0
	})
	a := testAddr(1)
	b := testAddr(2)
	// Two private channels, so the "kept its old channel" assertion is not masked by the open
	// channel's reach-across rule.
	hello(t, hub, conn, a, "A", "GLOWBERRY-AAAA")
	hello(t, hub, conn, b, "B", "GLOWBERRY-BBBB")

	conn.reset()
	// B tries to move to the full AAAA channel.
	hub.Handle(build(Packet{Type: TypeBeacon, Name: "B", Channel: "GLOWBERRY-AAAA"}), b)
	p, _ := ParsePacket(conn.to(b)[0])
	if p.Type != TypeNotice || p.Sequence != NoticeChannelFull {
		t.Fatalf("expected CHANNEL_FULL, got %+v", p)
	}
	// B must still be on BBBB, so an A audio frame on AAAA does not reach B.
	conn.reset()
	hub.Handle(build(Packet{Type: TypeAudio, Name: "A", Channel: "GLOWBERRY-AAAA", Payload: []byte{1}}), a)
	if got := conn.to(b); len(got) != 0 {
		t.Fatalf("a refused join must leave B off the full channel, got %d", len(got))
	}
}

func TestPerIPCap(t *testing.T) {
	hub, conn := newTestHub(t, func(c *Config) { c.MaxClientsPerIP = 1; c.MaxClients = 100 })
	ip := net.IPv4(10, 1, 1, 1)
	hello(t, hub, conn, &net.UDPAddr{IP: ip, Port: 1001}, "A", "world")
	conn.reset()
	addr := &net.UDPAddr{IP: ip, Port: 1002}
	hub.Handle(build(Packet{Type: TypeHello, Name: "B", Channel: "world"}), addr)
	p, _ := ParsePacket(conn.to(addr)[0])
	if p.Type != TypeNotice || p.Sequence != NoticeServerFull {
		t.Fatalf("expected SERVER_FULL for the per-ip cap, got %+v", p)
	}
}

func TestRateLimiterDropsABurst(t *testing.T) {
	hub, conn := newTestHub(t, func(c *Config) {
		c.RatePerClientPPS = 10
		c.RateBurstPackets = 5
		c.RatePerClientBPS = 100000
		c.RateBurstBytes = 100000
	})
	addr := testAddr(1)
	hello(t, hub, conn, addr, "A", "world")

	delivered := 0
	for i := 0; i < 20; i++ {
		conn.reset()
		hub.Handle(build(Packet{Type: TypeAudio, Channel: "world", Payload: []byte{1}}), addr)
		delivered += len(conn.to(addr))
	}
	if delivered > 5 {
		t.Fatalf("rate limiter let %d of 20 through a burst of 5", delivered)
	}
}

func TestIdleEviction(t *testing.T) {
	hub, conn := newTestHub(t, func(c *Config) { c.IdleTimeoutSec = 5 })
	addr := testAddr(1)
	hello(t, hub, conn, addr, "A", "world")

	// A beacon keeps the session alive.
	hub.Handle(build(Packet{Type: TypeBeacon, Channel: "world"}), addr)
	if evicted := hub.Reap(hub.now().Add(4 * time.Second)); len(evicted) != 0 {
		t.Fatalf("a fresh client must not be evicted, got %v", evicted)
	}
	// Past the timeout with no traffic, it goes.
	if evicted := hub.Reap(hub.now().Add(10 * time.Second)); len(evicted) != 1 {
		t.Fatalf("expected one eviction, got %v", evicted)
	}
	if len(hub.Snapshot()) != 0 {
		t.Fatal("evicted client must be gone")
	}
}

func TestKeepalivePingGetsAPong(t *testing.T) {
	hub, conn := newTestHub(t, nil)
	addr := testAddr(1)
	id := hello(t, hub, conn, addr, "A", "world")
	conn.reset()
	hub.Handle(build(Packet{Type: TypePing, Channel: "world"}), addr)
	got := conn.to(addr)
	if len(got) != 1 {
		t.Fatalf("expected a PONG, got %d", len(got))
	}
	p, _ := ParsePacket(got[0])
	if p.Type != TypePong || p.ClientID != id {
		t.Fatalf("pong = %+v", p)
	}
}

func TestPacketFromAnUnregisteredAddressIsDropped(t *testing.T) {
	hub, conn := newTestHub(t, nil)
	hub.Handle(build(Packet{Type: TypeAudio, Channel: "world", Payload: []byte{1}}), testAddr(9))
	if len(conn.sent) != 0 {
		t.Fatalf("an unregistered address must get nothing, got %d", len(conn.sent))
	}
}

func TestChannelCountTracksMembership(t *testing.T) {
	hub, conn := newTestHub(t, nil)
	hello(t, hub, conn, testAddr(1), "A", "team")
	hello(t, hub, conn, testAddr(2), "B", "team")
	hello(t, hub, conn, testAddr(3), "C", "world")
	if got := hub.channelCount(); got != 2 {
		t.Fatalf("channels = %d, want 2", got)
	}
	hub.Handle(build(Packet{Type: TypeBye, Channel: "team"}), testAddr(1))
	hub.Handle(build(Packet{Type: TypeBye, Channel: "team"}), testAddr(2))
	if got := hub.channelCount(); got != 1 {
		t.Fatalf("channels = %d, want 1 after the team emptied", got)
	}
}
