package main

import (
	"context"
	"net"
	"testing"
	"time"
)

// TestEndToEndOverRealUDP drives two clients through a real loopback socket, so the read loop,
// the hub and the write path are exercised together rather than through the fake connection.
// This is the test that would catch a framing mistake that unit tests with build/ParsePacket
// cannot: the bytes really travel.
func TestEndToEndOverRealUDP(t *testing.T) {
	cfg := DefaultConfig()
	cfg.Listen = "127.0.0.1:0"
	cfg.HTTPListen = ""
	if err := cfg.Validate(); err != nil {
		t.Fatal(err)
	}
	udpAddr, err := net.ResolveUDPAddr("udp", cfg.Listen)
	if err != nil {
		t.Fatal(err)
	}
	server, err := net.ListenUDP("udp", udpAddr)
	if err != nil {
		t.Fatal(err)
	}
	defer server.Close()

	hub := NewHub(cfg, NewLogger("error"), NewMetrics(), server)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go readLoop(ctx, server, hub, NewLogger("error"))

	// Two client sockets.
	clientA, err := net.ListenUDP("udp", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1), Port: 0})
	if err != nil {
		t.Fatal(err)
	}
	defer clientA.Close()
	clientB, err := net.ListenUDP("udp", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1), Port: 0})
	if err != nil {
		t.Fatal(err)
	}
	defer clientB.Close()

	serverAddr := server.LocalAddr().(*net.UDPAddr)

	// HELLO both.
	send(t, clientA, serverAddr, build(Packet{Type: TypeHello, Name: "A", Channel: "world"}))
	idA := readHelloAck(t, clientA)
	send(t, clientB, serverAddr, build(Packet{Type: TypeHello, Name: "B", Channel: "world"}))
	_ = readHelloAck(t, clientB)

	// A sends audio; B must receive it with A's assigned id.
	send(t, clientA, serverAddr, build(Packet{Type: TypeAudio, ClientID: 999, Name: "A",
		Channel: "world", Codec: CodecOpus, Payload: []byte{10, 20, 30}}))

	buf := make([]byte, MaxDatagram)
	clientB.SetReadDeadline(time.Now().Add(2 * time.Second))
	n, _, err := clientB.ReadFromUDP(buf)
	if err != nil {
		t.Fatalf("B did not receive the relayed audio: %v", err)
	}
	p, err := ParsePacket(buf[:n])
	if err != nil {
		t.Fatalf("relayed parse: %v", err)
	}
	if p.Type != TypeAudio || p.ClientID != idA {
		t.Fatalf("relayed packet = %+v, want audio with id %d", p, idA)
	}
	if len(p.Payload) != 3 || p.Payload[0] != 10 {
		t.Fatalf("relayed payload = %v", p.Payload)
	}
}

func send(t *testing.T, conn *net.UDPConn, to *net.UDPAddr, data []byte) {
	t.Helper()
	if _, err := conn.WriteToUDP(data, to); err != nil {
		t.Fatalf("send: %v", err)
	}
}

func readHelloAck(t *testing.T, conn *net.UDPConn) uint64 {
	t.Helper()
	buf := make([]byte, MaxDatagram)
	conn.SetReadDeadline(time.Now().Add(2 * time.Second))
	n, _, err := conn.ReadFromUDP(buf)
	if err != nil {
		t.Fatalf("no HELLO_ACK: %v", err)
	}
	p, err := ParsePacket(buf[:n])
	if err != nil || p.Type != TypeHelloAck {
		t.Fatalf("expected HELLO_ACK, got %+v err=%v", p, err)
	}
	return p.ClientID
}

// TestCosmeticManifestIsRelayedToChannelPeers is the end-to-end proof that a cosmetic manifest
// travels the relay: the same fan-out rule as audio, but as an opaque payload the server never
// decodes. It also pins that a manifest does NOT leak across a private channel, which is the
// property that makes "cosmetics are visible to peers in your session" honest.
func TestCosmeticManifestIsRelayedToChannelPeers(t *testing.T) {
	cfg := DefaultConfig()
	cfg.Listen = "127.0.0.1:0"
	cfg.HTTPListen = ""
	if err := cfg.Validate(); err != nil {
		t.Fatal(err)
	}
	udpAddr, err := net.ResolveUDPAddr("udp", cfg.Listen)
	if err != nil {
		t.Fatal(err)
	}
	server, err := net.ListenUDP("udp", udpAddr)
	if err != nil {
		t.Fatal(err)
	}
	defer server.Close()

	hub := NewHub(cfg, NewLogger("error"), NewMetrics(), server)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go readLoop(ctx, server, hub, NewLogger("error"))

	clientA, err := net.ListenUDP("udp", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1), Port: 0})
	if err != nil {
		t.Fatal(err)
	}
	defer clientA.Close()
	clientB, err := net.ListenUDP("udp", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1), Port: 0})
	if err != nil {
		t.Fatal(err)
	}
	defer clientB.Close()

	serverAddr := server.LocalAddr().(*net.UDPAddr)

	// Both in the open world channel, so they can hear each other.
	send(t, clientA, serverAddr, build(Packet{Type: TypeHello, Name: "A", Channel: "world"}))
	idA := readHelloAck(t, clientA)
	send(t, clientB, serverAddr, build(Packet{Type: TypeHello, Name: "B", Channel: "world"}))
	_ = readHelloAck(t, clientB)

	// A advertises its cosmetics. The payload is the launcher's CosmeticSyncProtocol datagram;
	// here it is opaque bytes, which is exactly the point -- the relay must not need to parse it.
	manifest := []byte{'C', 'S', 1, 1, 0, 4, 'c', 'a', 'p', 'e'}
	send(t, clientA, serverAddr, build(Packet{Type: TypeCosmeticManifest, ClientID: 999,
		Name: "A", Channel: "world", Payload: manifest}))

	buf := make([]byte, MaxDatagram)
	clientB.SetReadDeadline(time.Now().Add(2 * time.Second))
	n, _, err := clientB.ReadFromUDP(buf)
	if err != nil {
		t.Fatalf("B did not receive the relayed manifest: %v", err)
	}
	p, err := ParsePacket(buf[:n])
	if err != nil {
		t.Fatalf("relayed manifest parse: %v", err)
	}
	if p.Type != TypeCosmeticManifest || p.ClientID != idA {
		t.Fatalf("relayed packet = %+v, want manifest with id %d", p, idA)
	}
	if string(p.Payload) != string(manifest) {
		t.Fatalf("manifest payload = %v, want %v (must be relayed unchanged)", p.Payload, manifest)
	}
}
