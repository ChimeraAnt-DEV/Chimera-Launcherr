package main

import (
	"bytes"
	"testing"
)

func TestHelloAckRoundTripsWithClientID(t *testing.T) {
	data := BuildHelloAck(4242, "Test Relay", 3000)
	p, err := ParsePacket(data)
	if err != nil {
		t.Fatalf("parse: %v", err)
	}
	if p.Type != TypeHelloAck {
		t.Fatalf("type = %d, want %d", p.Type, TypeHelloAck)
	}
	if p.ClientID != 4242 {
		t.Fatalf("client id = %d, want 4242", p.ClientID)
	}
	if string(p.Payload) != "Test Relay" {
		t.Fatalf("payload = %q", p.Payload)
	}
	if p.Sequence != 3000 {
		t.Fatalf("heartbeat = %d, want 3000", p.Sequence)
	}
}

func TestBeaconFieldsSurviveARoundTrip(t *testing.T) {
	data := build(Packet{
		Type: TypeBeacon, ClientID: 7, Name: "Phone", Channel: "team",
		Visibility: VisibilityPrivate, ChannelName: "Squad", Capacity: 8,
		Level: 0.5, Muted: true, X: 1.5, Y: 64, Z: -3.25, Sequence: 11,
		Codec: CodecOpus,
	})
	p, err := ParsePacket(data)
	if err != nil {
		t.Fatalf("parse: %v", err)
	}
	if p.Name != "Phone" || p.Channel != "team" || p.ChannelName != "Squad" {
		t.Fatalf("strings lost: %+v", p)
	}
	if p.Visibility != VisibilityPrivate || p.Capacity != 8 || !p.Muted {
		t.Fatalf("state lost: %+v", p)
	}
	if p.Level != 0.5 || p.X != 1.5 || p.Y != 64 || p.Z != -3.25 {
		t.Fatalf("floats lost: %+v", p)
	}
	if p.Codec != CodecOpus {
		t.Fatalf("codec = %d", p.Codec)
	}
}

func TestAudioPayloadSurvivesARoundTrip(t *testing.T) {
	payload := []byte{1, 2, 3, 4, 5, 6, 7}
	data := build(Packet{Type: TypeAudio, ClientID: 1, Channel: "world",
		Codec: CodecOpus, Payload: payload})
	p, err := ParsePacket(data)
	if err != nil {
		t.Fatalf("parse: %v", err)
	}
	if !bytes.Equal(p.Payload, payload) {
		t.Fatalf("payload = %v, want %v", p.Payload, payload)
	}
}

func TestPatchClientIDRewritesOnlyTheIdentity(t *testing.T) {
	data := build(Packet{Type: TypeAudio, ClientID: 1, Name: "A", Channel: "world",
		Codec: CodecOpus, Payload: []byte{9, 9}})
	before := make([]byte, len(data))
	copy(before, data)

	PatchClientID(data, 999)
	if got := ClientIDOf(data); got != 999 {
		t.Fatalf("patched id = %d, want 999", got)
	}
	// Everything except the 8 id bytes must be untouched.
	for i := range data {
		if i >= clientIDOffset && i < clientIDOffset+8 {
			continue
		}
		if data[i] != before[i] {
			t.Fatalf("byte %d changed: %d != %d", i, data[i], before[i])
		}
	}
	p, err := ParsePacket(data)
	if err != nil || p.ClientID != 999 || p.Name != "A" || len(p.Payload) != 2 {
		t.Fatalf("re-parse after patch: %+v err=%v", p, err)
	}
}

func TestForeignAndTruncatedDatagramsAreRejected(t *testing.T) {
	cases := []struct {
		name string
		data []byte
	}{
		{"empty", nil},
		{"foreign", []byte{'X', 'Y', 4, 1}},
		{"short", []byte{'C', 'V'}},
	}
	for _, tc := range cases {
		if _, err := ParsePacket(tc.data); err == nil {
			t.Fatalf("%s: expected an error", tc.name)
		}
	}

	good := build(Packet{Type: TypeBeacon, ClientID: 1, Channel: "world", Name: "n"})
	if _, err := ParsePacket(good[:len(good)-3]); err == nil {
		t.Fatal("truncated body: expected an error")
	}
}

func TestWrongVersionIsRejectedButRecognised(t *testing.T) {
	data := BuildHelloAck(1, "s", 3000)
	data[2] = 3
	_, err := ParsePacket(data)
	if err != ErrBadVersion {
		t.Fatalf("err = %v, want ErrBadVersion", err)
	}
}

func TestAnOverlongPayloadLengthIsRejected(t *testing.T) {
	good := build(Packet{Type: TypeAudio, ClientID: 1, Channel: "world", Payload: []byte{1}})
	// The payload length is the final 4 bytes; set it absurdly high.
	good[len(good)-4] = 0x7f
	if _, err := ParsePacket(good); err != ErrBadPayload {
		t.Fatalf("err = %v, want ErrBadPayload", err)
	}
}

func TestNormalizeChannelMirrorsTheClient(t *testing.T) {
	cases := map[string]string{
		"":               ChannelWorld,
		"   ":            ChannelWorld,
		"Team":           "team",
		" TEAM ":         "team",
		"GLOWBERRY-7F2Q": "glowberry-7f2q",
	}
	for in, want := range cases {
		if got := NormalizeChannel(in); got != want {
			t.Errorf("NormalizeChannel(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestCanHearMatchesTheChannelRule(t *testing.T) {
	cases := []struct {
		listener, talker string
		want             bool
	}{
		{"world", "world", true},
		{"team", "team", true},
		{"world", "team", true},
		{"team", "world", true},
		{"team", "squad", false},
	}
	for _, tc := range cases {
		if got := CanHear(tc.listener, tc.talker); got != tc.want {
			t.Errorf("CanHear(%q,%q) = %v, want %v", tc.listener, tc.talker, got, tc.want)
		}
	}
}

func TestServerNoticeCarriesItsReason(t *testing.T) {
	data := BuildNotice(0, NoticeServerFull, "server is full")
	p, err := ParsePacket(data)
	if err != nil {
		t.Fatalf("parse: %v", err)
	}
	if p.Type != TypeNotice || p.Sequence != NoticeServerFull || string(p.Payload) != "server is full" {
		t.Fatalf("notice = %+v", p)
	}
}
