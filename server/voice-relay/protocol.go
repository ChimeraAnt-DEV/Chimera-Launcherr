package main

import (
	"encoding/binary"
	"errors"
	"math"
)

// The relay speaks voice protocol v4 only. v1-v3 are the LAN-multicast formats; a client reaches
// this server only after it has the relay feature, which is the same change that introduced v4,
// so there is no legacy peer to stay compatible with here. The launcher's own VoiceProtocol
// still decodes v1-v4 for the multicast path.
const (
	magic0  = 'C'
	magic1  = 'V'
	version = 4

	// Client-originated types.
	TypeBeacon = 1
	TypeAudio  = 2
	TypeBye    = 3
	TypeHello  = 4

	// Server-originated types.
	TypeHelloAck = 5
	TypePing     = 6
	TypePong     = 7
	TypeNotice   = 8

	// Codec bytes. The relay never decodes audio, so it only carries the byte through to the
	// other clients; it exists so a listener can tell a raw-PCM peer from an Opus one.
	CodecPCM  = 0
	CodecOpus = 1

	VisibilityPublic  = 0
	VisibilityPrivate = 1

	// MaxPayload bounds a single datagram's audio body so a hostile packet cannot make the
	// server allocate without limit.
	MaxPayload = 4096
	// MaxString bounds every length-prefixed string on the wire.
	MaxString = 255
	// MaxDatagram is the largest datagram the server will read at all.
	MaxDatagram = MaxPayload + 512
)

// Notice reason codes, carried in a NOTICE packet's sequence field.
const (
	NoticeServerFull  = 1
	NoticeBadPassword = 2
	NoticeChannelFull = 3
	NoticeBadProtocol = 4
)

// ChannelWorld is the open channel everyone hears across, matching VoiceChannel.WORLD in the
// launcher. It is the fallback for a blank channel id.
const ChannelWorld = "world"

var (
	ErrNotOurs    = errors.New("not a voice datagram")
	ErrBadVersion = errors.New("unsupported protocol version")
	ErrTruncated  = errors.New("truncated datagram")
	ErrBadPayload = errors.New("invalid payload length")
)

// Packet is one decoded datagram. The same shape is used in both directions; a relayed packet
// keeps the sender's type (beacon/audio/bye) and only its ClientID is rewritten.
type Packet struct {
	Type        byte
	ClientID    uint64
	PeerID      string
	Name        string
	Channel     string
	Visibility  byte
	ChannelName string
	Capacity    int32
	Level       float32
	Muted       bool
	X, Y, Z     float32
	Sequence    int32
	Codec       byte
	Payload     []byte
}

// clientIDOffset is where the 8-byte client id sits: magic(2) + version(1) + type(1). The relay
// patches it in place rather than re-encoding, so a relayed frame is byte-for-byte the sender's
// except for this field.
const clientIDOffset = 4

type reader struct {
	buf []byte
	pos int
}

func (r *reader) take(n int) ([]byte, error) {
	if n < 0 || r.pos+n > len(r.buf) {
		return nil, ErrTruncated
	}
	out := r.buf[r.pos : r.pos+n]
	r.pos += n
	return out, nil
}

func (r *reader) u8() (byte, error) {
	b, err := r.take(1)
	if err != nil {
		return 0, err
	}
	return b[0], nil
}

func (r *reader) u64() (uint64, error) {
	b, err := r.take(8)
	if err != nil {
		return 0, err
	}
	return binary.BigEndian.Uint64(b), nil
}

func (r *reader) i32() (int32, error) {
	b, err := r.take(4)
	if err != nil {
		return 0, err
	}
	return int32(binary.BigEndian.Uint32(b)), nil
}

func (r *reader) f32() (float32, error) {
	b, err := r.take(4)
	if err != nil {
		return 0, err
	}
	return math.Float32frombits(binary.BigEndian.Uint32(b)), nil
}

func (r *reader) str() (string, error) {
	n, err := r.u8()
	if err != nil {
		return "", err
	}
	b, err := r.take(int(n))
	if err != nil {
		return "", err
	}
	return string(b), nil
}

func (r *reader) remaining() int {
	return len(r.buf) - r.pos
}

// ParsePacket decodes a datagram, returning a sentinel error for anything that is not a
// well-formed v4 packet. Every read is bounds-checked, so a truncated or hostile datagram
// produces an error rather than a panic.
func ParsePacket(data []byte) (Packet, error) {
	var p Packet
	r := &reader{buf: data}
	head, err := r.take(2)
	if err != nil || head[0] != magic0 || head[1] != magic1 {
		return p, ErrNotOurs
	}
	v, err := r.u8()
	if err != nil {
		return p, ErrTruncated
	}
	if v != version {
		return p, ErrBadVersion
	}
	if p.Type, err = r.u8(); err != nil {
		return p, ErrTruncated
	}
	if p.ClientID, err = r.u64(); err != nil {
		return p, ErrTruncated
	}
	// The peer id is the sender's own short identity, distinct from the server-assigned client
	// id. The relay never uses it, but it must still be consumed or every field after it would be
	// read from the wrong offset.
	if p.PeerID, err = r.str(); err != nil {
		return p, ErrTruncated
	}
	if p.Name, err = r.str(); err != nil {
		return p, ErrTruncated
	}
	if p.Channel, err = r.str(); err != nil {
		return p, ErrTruncated
	}
	if p.Visibility, err = r.u8(); err != nil {
		return p, ErrTruncated
	}
	if p.ChannelName, err = r.str(); err != nil {
		return p, ErrTruncated
	}
	if p.Capacity, err = r.i32(); err != nil {
		return p, ErrTruncated
	}
	if p.Level, err = r.f32(); err != nil {
		return p, ErrTruncated
	}
	muted, err := r.u8()
	if err != nil {
		return p, ErrTruncated
	}
	p.Muted = muted != 0
	if p.X, err = r.f32(); err != nil {
		return p, ErrTruncated
	}
	if p.Y, err = r.f32(); err != nil {
		return p, ErrTruncated
	}
	if p.Z, err = r.f32(); err != nil {
		return p, ErrTruncated
	}
	if p.Sequence, err = r.i32(); err != nil {
		return p, ErrTruncated
	}
	if p.Codec, err = r.u8(); err != nil {
		return p, ErrTruncated
	}
	length, err := r.i32()
	if err != nil {
		return p, ErrTruncated
	}
	if length < 0 || length > MaxPayload || r.remaining() < int(length) {
		return p, ErrBadPayload
	}
	if p.Payload, err = r.take(int(length)); err != nil {
		return p, ErrBadPayload
	}
	p.Channel = NormalizeChannel(p.Channel)
	p.Visibility = normalizeVisibility(p.Visibility)
	p.Capacity = normalizeCapacity(p.Capacity)
	p.Level = clampLevel(p.Level)
	return p, nil
}

// PatchClientID writes an assigned id into a received datagram in place. A no-op on a datagram
// too short to hold the field, which ParsePacket has already rejected in practice.
func PatchClientID(data []byte, id uint64) {
	if len(data) < clientIDOffset+8 {
		return
	}
	binary.BigEndian.PutUint64(data[clientIDOffset:clientIDOffset+8], id)
}

// ClientIDOf reads the id back out of a datagram, for tests and diagnostics.
func ClientIDOf(data []byte) uint64 {
	if len(data) < clientIDOffset+8 {
		return 0
	}
	return binary.BigEndian.Uint64(data[clientIDOffset : clientIDOffset+8])
}

func appendString(buf []byte, value string) []byte {
	if len(value) > MaxString {
		value = value[:MaxString]
	}
	buf = append(buf, byte(len(value)))
	return append(buf, value...)
}

// build encodes a packet. Only server-originated packets are built here; relayed frames are the
// sender's own bytes with the client id patched.
func build(p Packet) []byte {
	buf := make([]byte, 0, 128+len(p.Payload))
	buf = append(buf, magic0, magic1, version, p.Type)
	buf = binary.BigEndian.AppendUint64(buf, p.ClientID)
	buf = appendString(buf, p.PeerID)
	buf = appendString(buf, p.Name)
	buf = appendString(buf, p.Channel)
	buf = append(buf, normalizeVisibility(p.Visibility))
	buf = appendString(buf, p.ChannelName)
	buf = binary.BigEndian.AppendUint32(buf, uint32(p.Capacity))
	buf = binary.BigEndian.AppendUint32(buf, math.Float32bits(p.Level))
	if p.Muted {
		buf = append(buf, 1)
	} else {
		buf = append(buf, 0)
	}
	buf = binary.BigEndian.AppendUint32(buf, math.Float32bits(p.X))
	buf = binary.BigEndian.AppendUint32(buf, math.Float32bits(p.Y))
	buf = binary.BigEndian.AppendUint32(buf, math.Float32bits(p.Z))
	buf = binary.BigEndian.AppendUint32(buf, uint32(p.Sequence))
	buf = append(buf, p.Codec)
	buf = binary.BigEndian.AppendUint32(buf, uint32(len(p.Payload)))
	return append(buf, p.Payload...)
}

// BuildHelloAck is the server's reply to a HELLO. The client id is the whole point: it is the
// stable identity the client uses from then on, and the server name rides in the payload with
// the heartbeat interval in the sequence field.
func BuildHelloAck(clientID uint64, serverName string, heartbeatMs int32) []byte {
	return build(Packet{
		Type:     TypeHelloAck,
		ClientID: clientID,
		Sequence: heartbeatMs,
		Payload:  []byte(serverName),
	})
}

// BuildPong echoes a keepalive.
func BuildPong(clientID uint64) []byte {
	return build(Packet{Type: TypePong, ClientID: clientID})
}

// BuildNotice tells a client why the server refused something. The reason is in the sequence
// field and the human-readable message in the payload.
func BuildNotice(clientID uint64, reason int32, message string) []byte {
	return build(Packet{Type: TypeNotice, ClientID: clientID, Sequence: reason, Payload: []byte(message)})
}

// NormalizeChannel lower-cases and trims a channel id, mapping blank to the open channel. It
// mirrors VoiceChannel.normalize so both ends agree on which ids are the same room.
func NormalizeChannel(channel string) string {
	out := make([]byte, 0, len(channel))
	for i := 0; i < len(channel); i++ {
		c := channel[i]
		if c == ' ' || c == '\t' || c == '\n' || c == '\r' {
			continue
		}
		if c >= 'A' && c <= 'Z' {
			c += 'a' - 'A'
		}
		out = append(out, c)
	}
	if len(out) == 0 {
		return ChannelWorld
	}
	return string(out)
}

func normalizeVisibility(v byte) byte {
	if v == VisibilityPrivate {
		return VisibilityPrivate
	}
	return VisibilityPublic
}

func normalizeCapacity(c int32) int32 {
	if c <= 0 {
		return 0
	}
	if c > 100 {
		return 100
	}
	return c
}

func clampLevel(level float32) float32 {
	if math.IsNaN(float64(level)) || level <= 0 {
		return 0
	}
	if level > 1 {
		return 1
	}
	return level
}

// CanHear is the relay's copy of VoiceChannel.canHear: the same channel always hears, and the
// open channel reaches across in both directions. Keeping the rule identical on both ends is what
// makes "switch to a team channel" behave the same over the relay as on the LAN.
func CanHear(listenerChannel, talkerChannel string) bool {
	listener := NormalizeChannel(listenerChannel)
	talker := NormalizeChannel(talkerChannel)
	if listener == talker {
		return true
	}
	return listener == ChannelWorld || talker == ChannelWorld
}
