package main

import (
	"encoding/hex"
	"testing"
)

// These two literals are produced by the launcher's
// VoiceProtocolGoldenVectorTest. They pin the cross-language wire layout: if the Java encoder
// gains, drops or reorders a field, TestParseGoldenBeacon/TestParseGoldenAudio below fail here.
const (
	goldenBeaconHex = "435604010000000000001092066162633132330550686F6E65047465616D0105537175616400" +
		"0000083F000000013F80000042800000C0400000000000110100000000"
	goldenAudioHex = "4356040200000000000000070470656572024D6505776F726C64000000000000000000000000" +
		"00000000000000000000000000000301000000050908070605"
)

func mustHex(t *testing.T, s string) []byte {
	t.Helper()
	b, err := hex.DecodeString(s)
	if err != nil {
		t.Fatalf("bad hex fixture: %v", err)
	}
	return b
}

// TestParseGoldenBeacon decodes the exact bytes the launcher sends for a private team beacon and
// asserts every field landed where the relay expects it.
func TestParseGoldenBeacon(t *testing.T) {
	p, err := ParsePacket(mustHex(t, goldenBeaconHex))
	if err != nil {
		t.Fatalf("parse failed: %v", err)
	}
	if p.Type != TypeBeacon {
		t.Errorf("type = %d, want %d", p.Type, TypeBeacon)
	}
	if p.ClientID != 4242 {
		t.Errorf("clientID = %d, want 4242", p.ClientID)
	}
	if p.PeerID != "abc123" {
		t.Errorf("peerID = %q, want abc123", p.PeerID)
	}
	if p.Name != "Phone" {
		t.Errorf("name = %q, want Phone", p.Name)
	}
	if p.Channel != "team" {
		t.Errorf("channel = %q, want team", p.Channel)
	}
	if p.Visibility != VisibilityPrivate {
		t.Errorf("visibility = %d, want private", p.Visibility)
	}
	if p.ChannelName != "Squad" {
		t.Errorf("channelName = %q, want Squad", p.ChannelName)
	}
	if p.Capacity != 8 {
		t.Errorf("capacity = %d, want 8", p.Capacity)
	}
	if p.Level != 0.5 {
		t.Errorf("level = %v, want 0.5", p.Level)
	}
	if !p.Muted {
		t.Error("muted = false, want true")
	}
	if p.X != 1 || p.Y != 64 || p.Z != -3 {
		t.Errorf("position = (%v,%v,%v), want (1,64,-3)", p.X, p.Y, p.Z)
	}
	if p.Sequence != 17 {
		t.Errorf("sequence = %d, want 17", p.Sequence)
	}
	if p.Codec != CodecOpus {
		t.Errorf("codec = %d, want opus", p.Codec)
	}
}

// TestParseGoldenAudio decodes an Opus audio frame and checks its payload survived intact.
func TestParseGoldenAudio(t *testing.T) {
	p, err := ParsePacket(mustHex(t, goldenAudioHex))
	if err != nil {
		t.Fatalf("parse failed: %v", err)
	}
	if p.Type != TypeAudio {
		t.Errorf("type = %d, want %d", p.Type, TypeAudio)
	}
	if p.ClientID != 7 {
		t.Errorf("clientID = %d, want 7", p.ClientID)
	}
	if p.PeerID != "peer" {
		t.Errorf("peerID = %q, want peer", p.PeerID)
	}
	if p.Name != "Me" {
		t.Errorf("name = %q, want Me", p.Name)
	}
	if p.Channel != ChannelWorld {
		t.Errorf("channel = %q, want world", p.Channel)
	}
	if p.Sequence != 3 {
		t.Errorf("sequence = %d, want 3", p.Sequence)
	}
	if p.Codec != CodecOpus {
		t.Errorf("codec = %d, want opus", p.Codec)
	}
	want := []byte{9, 8, 7, 6, 5}
	if len(p.Payload) != len(want) {
		t.Fatalf("payload length = %d, want %d", len(p.Payload), len(want))
	}
	for i := range want {
		if p.Payload[i] != want[i] {
			t.Fatalf("payload[%d] = %d, want %d", i, p.Payload[i], want[i])
		}
	}
}

// TestPatchClientIDRewritesOnlyTheID checks the relay's in-place rewrite leaves the rest of the
// datagram byte-identical, which is what lets it forward without re-encoding.
func TestPatchClientIDRewritesOnlyTheID(t *testing.T) {
	data := mustHex(t, goldenAudioHex)
	PatchClientID(data, 0xDEADBEEF)
	if got := ClientIDOf(data); got != 0xDEADBEEF {
		t.Errorf("clientID after patch = %x, want deadbeef", got)
	}
	p, err := ParsePacket(data)
	if err != nil {
		t.Fatalf("parse after patch failed: %v", err)
	}
	if p.ClientID != 0xDEADBEEF {
		t.Errorf("parsed clientID = %x, want deadbeef", p.ClientID)
	}
	if p.PeerID != "peer" || p.Sequence != 3 || len(p.Payload) != 5 {
		t.Error("patching the client id disturbed another field")
	}
}

// TestBuildProducesAParseableV4Frame round-trips a server-originated packet through build/parse,
// so the server's own encoder is held to the same layout as the client's.
func TestBuildProducesAParseableV4Frame(t *testing.T) {
	data := BuildHelloAck(99, "relay", 3000)
	p, err := ParsePacket(data)
	if err != nil {
		t.Fatalf("parse failed: %v", err)
	}
	if p.Type != TypeHelloAck || p.ClientID != 99 || p.Sequence != 3000 {
		t.Errorf("round trip gave type=%d id=%d seq=%d", p.Type, p.ClientID, p.Sequence)
	}
	if string(p.Payload) != "relay" {
		t.Errorf("payload = %q, want relay", string(p.Payload))
	}
	if data[2] != version {
		t.Errorf("version byte = %d, want %d", data[2], version)
	}
}
