package main

import (
	"net"
	"sync"
	"time"
)

// packetConn is the write half of a UDP socket. Narrowing it here lets the hub be driven by a
// fake in tests while production passes the real *net.UDPConn.
type packetConn interface {
	WriteToUDP(b []byte, addr *net.UDPAddr) (int, error)
}

// Client is one connected session. The server assigns the id; everything else is what the client
// last advertised. The id is authoritative: a relayed frame carries the sender's assigned id, so
// a listener can key on it without trusting the client's own claim.
type Client struct {
	id          uint64
	addr        *net.UDPAddr
	ip          string
	name        string
	deviceID    string
	channel     string
	channelName string
	visibility  byte
	capacity    int32
	level       float32
	muted       bool
	x, y, z     float32
	lastSeen    time.Time
	limiter     *RateLimiter
}

// Hub is the relay itself: it owns the session table, the channel membership counts, and the
// fan-out rule. It holds no socket of its own beyond the injected writer, which is what makes it
// testable end to end without a network.
type Hub struct {
	cfg     Config
	log     *Logger
	metrics *Metrics
	conn    packetConn
	now     func() time.Time
	bans    *BanList

	mu        sync.Mutex
	clients   map[uint64]*Client
	addrIndex map[string]uint64
	ipCount   map[string]int
	channels  map[string]int
	nextID    uint64
}

// NewHub builds a hub writing datagrams through conn.
func NewHub(cfg Config, log *Logger, metrics *Metrics, conn packetConn) *Hub {
	return &Hub{
		cfg:       cfg,
		log:       log,
		metrics:   metrics,
		conn:      conn,
		now:       time.Now,
		bans:      NewBanList(cfg.BannedIPs, cfg.BannedDevices, cfg.AuthFailuresBeforeBan, cfg.BanWindow()),
		clients:   map[uint64]*Client{},
		addrIndex: map[string]uint64{},
		ipCount:   map[string]int{},
		channels:  map[string]int{},
	}
}

// Bans exposes the ban list so the admin endpoint and reaper can act on it.
func (h *Hub) Bans() *BanList { return h.bans }

// Handle processes one received datagram. It never returns an error: a malformed or hostile
// packet is counted and dropped, because a relay must not be crashable by its input.
func (h *Hub) Handle(data []byte, addr *net.UDPAddr) {
	now := h.now()
	h.metrics.packetsIn.Add(1)
	h.metrics.bytesIn.Add(int64(len(data)))

	p, err := ParsePacket(data)
	if err != nil {
		h.metrics.packetsDroppedBad.Add(1)
		if err == ErrBadVersion && len(data) >= 2 && data[0] == magic0 && data[1] == magic1 {
			// Only answer a datagram that is actually ours; answering anything with our magic
			// would be a reflection vector.
			h.notice(addr, 0, NoticeBadProtocol, "this server speaks voice protocol v4")
		}
		h.log.Debug("dropped malformed datagram", map[string]any{
			"from": addr.String(), "bytes": len(data), "err": err.Error(),
		})
		return
	}

	switch p.Type {
	case TypeHello:
		h.onHello(p, addr, now)
	case TypeBeacon, TypeAudio, TypeBye:
		h.onClientPacket(p, data, addr, now)
	case TypePing:
		if c := h.byAddr(addr); c != nil {
			c.lastSeen = now
			h.send(BuildPong(c.id), c.addr)
		}
	case TypePong:
		if c := h.byAddr(addr); c != nil {
			c.lastSeen = now
		}
	default:
		h.metrics.packetsDroppedBad.Add(1)
	}
}

// authenticate decides whether a HELLO's credential is acceptable, returning the device id to
// bind the session to. Two schemes, in order:
//
//   - Token auth (the real one): when TokenSecret is set, the payload must be a signed token whose
//     signature and expiry verify. The device id baked into the token must match the one the
//     client claims in the packet, so a token cannot be presented for a different device.
//   - Password (the fallback): the payload must equal the shared password.
//
// An empty TokenSecret and empty Password is an open relay, which returns deviceUnbound and is
// what a first deployment looks like before it is locked down.
const deviceUnbound = ""

func (h *Hub) authenticate(p Packet, ip string, now time.Time) (device string, ok bool, reason string, notice int32) {
	if h.bans.Banned(ip, p.DeviceID, now) {
		return "", false, "banned", NoticeBanned
	}
	if h.cfg.TokenSecret != "" {
		token := string(p.Payload)
		device, err := VerifyToken([]byte(h.cfg.TokenSecret), token, now)
		if err != nil {
			return "", false, "bad token: " + err.Error(), NoticeBadToken
		}
		if p.DeviceID != "" && p.DeviceID != device {
			return "", false, "token device mismatch", NoticeBadToken
		}
		return device, true, "", 0
	}
	if h.cfg.Password != "" && string(p.Payload) != h.cfg.Password {
		return "", false, "bad password", NoticeBadPassword
	}
	return p.DeviceID, true, "", 0
}

// onHello admits a session. The address index means a re-HELLO from the same socket replaces the
// previous session rather than accumulating one per attempt (a phone that reconnects every few
// seconds must not exhaust MaxClients).
func (h *Hub) onHello(p Packet, addr *net.UDPAddr, now time.Time) {
	ip := ipOf(addr)

	// A banned device is refused before any credential is checked: there is no point verifying a
	// token for someone who is not allowed to connect at all.
	if h.bans.Banned(ip, p.DeviceID, now) {
		h.metrics.rejectedBanned.Add(1)
		h.notice(addr, 0, NoticeBanned, "you are banned from this relay")
		h.log.Info("rejected hello: banned", map[string]any{"from": addr.String(), "device": p.DeviceID})
		return
	}

	device, ok, reason, notice := h.authenticate(p, ip, now)
	if !ok {
		// Count the failure against the address; enough of them auto-bans it. Only a credential
		// failure counts, not a busy server, so a legitimate user is never banned for being early.
		if notice == NoticeBadToken || notice == NoticeBadPassword {
			if h.bans.NoteAuthFailure(ip, now) {
				h.metrics.bansApplied.Add(1)
				h.log.Info("auto-banned after repeated auth failures", map[string]any{"ip": ip})
			}
		}
		h.metrics.rejectedAuth.Add(1)
		h.notice(addr, 0, notice, authMessage(reason))
		h.log.Info("rejected hello", map[string]any{"from": addr.String(), "reason": reason})
		return
	}

	h.mu.Lock()
	// A banned address that still holds a session is dropped here too, so an admin ban takes
	// effect on the next packet rather than waiting for the idle timeout.
	if h.bans.Banned(ip, device, now) {
		h.mu.Unlock()
		h.metrics.rejectedBanned.Add(1)
		h.notice(addr, 0, NoticeBanned, "you are banned from this relay")
		return
	}
	// Replace any session already bound to this socket.
	if oldID, ok := h.addrIndex[addr.String()]; ok {
		h.removeLocked(oldID, "replaced by a new hello")
	}
	if h.cfg.MaxClientsPerIP > 0 && h.ipCount[ip] >= h.cfg.MaxClientsPerIP {
		h.mu.Unlock()
		h.notice(addr, 0, NoticeServerFull, "too many connections from this address")
		h.log.Info("rejected hello: per-ip cap", map[string]any{"from": addr.String(), "ip": ip})
		return
	}
	if len(h.clients) >= h.cfg.MaxClients {
		h.mu.Unlock()
		h.metrics.rejectedFull.Add(1)
		h.notice(addr, 0, NoticeServerFull, "server is full")
		h.log.Info("rejected hello: server full", map[string]any{"from": addr.String()})
		return
	}

	h.nextID++
	id := h.nextID
	c := &Client{
		id:       id,
		addr:     addr,
		ip:       ip,
		name:     sanitizeName(p.Name),
		deviceID: device,
		channel:  p.Channel,
		lastSeen: now,
		limiter: NewRateLimiter(h.cfg.RatePerClientPPS, h.cfg.RateBurstPackets,
			h.cfg.RatePerClientBPS, h.cfg.RateBurstBytes, now),
	}
	h.clients[id] = c
	h.addrIndex[addr.String()] = id
	h.ipCount[ip]++
	h.channels[c.channel]++
	h.mu.Unlock()

	h.metrics.clients.Store(int64(h.clientCount()))
	h.metrics.channels.Store(int64(h.channelCount()))
	h.metrics.sessionsTotal.Add(1)
	h.metrics.noteClient(id, now)

	h.log.Info("client joined", map[string]any{
		"id": id, "from": addr.String(), "name": c.name, "channel": c.channel, "device": device,
	})
	h.send(BuildHelloAck(id, h.cfg.ServerName, int32(h.cfg.HeartbeatMs)), addr)
}

// authMessage keeps the reason a client sees generic; the specific cause is logged server-side.
func authMessage(reason string) string {
	if reason == "banned" {
		return "you are banned from this relay"
	}
	return "authentication failed"
}

// onClientPacket handles a beacon/audio/bye from a session that has already HELLOed.
func (h *Hub) onClientPacket(p Packet, data []byte, addr *net.UDPAddr, now time.Time) {
	h.mu.Lock()
	id, ok := h.addrIndex[addr.String()]
	if !ok {
		h.mu.Unlock()
		h.log.Debug("dropped packet from an unregistered address", map[string]any{"from": addr.String()})
		return
	}
	c := h.clients[id]
	if c == nil {
		h.mu.Unlock()
		return
	}
	if !c.limiter.Allow(len(data), now) {
		h.mu.Unlock()
		h.metrics.packetsDroppedRate.Add(1)
		return
	}

	c.lastSeen = now
	c.name = sanitizeName(p.Name)
	if p.DeviceID != "" {
		c.deviceID = p.DeviceID
	}
	c.visibility = p.Visibility
	c.channelName = p.ChannelName
	c.capacity = p.Capacity
	c.level = p.Level
	c.muted = p.Muted
	c.x, c.y, c.z = p.X, p.Y, p.Z

	// Channel changes are where the per-channel cap bites. A refused move leaves the client on
	// its old channel rather than dropping it, so a full room is a no-op, not a disconnect.
	if p.Channel != c.channel {
		if h.cfg.MaxChannelMembers > 0 && h.channels[p.Channel] >= h.cfg.MaxChannelMembers {
			h.mu.Unlock()
			h.metrics.rejectedChannel.Add(1)
			h.notice(addr, c.id, NoticeChannelFull, "channel is full")
			return
		}
		if h.channels[p.Channel] == 0 && len(h.channels) >= h.cfg.MaxChannels {
			h.mu.Unlock()
			h.metrics.rejectedChannel.Add(1)
			h.notice(addr, c.id, NoticeChannelFull, "too many channels")
			return
		}
		h.channels[c.channel]--
		if h.channels[c.channel] <= 0 {
			delete(h.channels, c.channel)
		}
		c.channel = p.Channel
		h.channels[c.channel]++
	}

	// Snapshot the recipients while holding the lock, then send outside it: WriteToUDP must not
	// hold the hub lock or one slow socket stalls every other session.
	recipients := make([]*Client, 0, len(h.clients))
	for _, other := range h.clients {
		if other.id == c.id {
			continue
		}
		if CanHear(other.channel, c.channel) {
			recipients = append(recipients, other)
		}
	}
	bye := p.Type == TypeBye
	if bye {
		h.removeLocked(c.id, "client said bye")
	}
	h.mu.Unlock()

	if bye {
		h.metrics.clients.Store(int64(h.clientCount()))
		h.metrics.channels.Store(int64(h.channelCount()))
		h.log.Info("client left", map[string]any{"id": c.id, "name": c.name})
	}

	// Patch the sender's assigned id into one copy, then fan it out. Patching once means the
	// relayed bytes are identical for every recipient and only one allocation happens per packet.
	out := make([]byte, len(data))
	copy(out, data)
	PatchClientID(out, c.id)

	for _, other := range recipients {
		h.send(out, other.addr)
	}

	if p.Type == TypeAudio {
		h.log.Debug("relayed audio", map[string]any{
			"id": c.id, "channel": c.channel, "codec": p.Codec,
			"bytes": len(p.Payload), "listeners": len(recipients),
		})
	}
}

// byAddr returns the session for an address, or nil.
func (h *Hub) byAddr(addr *net.UDPAddr) *Client {
	h.mu.Lock()
	defer h.mu.Unlock()
	id, ok := h.addrIndex[addr.String()]
	if !ok {
		return nil
	}
	return h.clients[id]
}

// Reap evicts sessions that have gone quiet for longer than the idle timeout. Called from a
// ticker; this is what removes a phone that lost signal without sending BYE.
func (h *Hub) Reap(now time.Time) []uint64 {
	timeout := h.cfg.IdleTimeout()
	var evicted []uint64
	h.mu.Lock()
	for id, c := range h.clients {
		if now.Sub(c.lastSeen) > timeout {
			evicted = append(evicted, id)
		}
	}
	for _, id := range evicted {
		h.removeLocked(id, "idle timeout")
	}
	h.mu.Unlock()
	h.bans.Sweep(now)

	if len(evicted) > 0 {
		h.metrics.clients.Store(int64(h.clientCount()))
		h.metrics.channels.Store(int64(h.channelCount()))
		for _, id := range evicted {
			h.log.Info("client evicted", map[string]any{"id": id, "reason": "idle"})
		}
	}
	return evicted
}

// removeLocked drops a session and fixes every index and counter it touched. Caller holds the lock.
func (h *Hub) removeLocked(id uint64, reason string) {
	c, ok := h.clients[id]
	if !ok {
		return
	}
	delete(h.clients, id)
	if cur, ok := h.addrIndex[c.addr.String()]; ok && cur == id {
		delete(h.addrIndex, c.addr.String())
	}
	if h.ipCount[c.ip] > 0 {
		h.ipCount[c.ip]--
		if h.ipCount[c.ip] == 0 {
			delete(h.ipCount, c.ip)
		}
	}
	if h.channels[c.channel] > 0 {
		h.channels[c.channel]--
		if h.channels[c.channel] <= 0 {
			delete(h.channels, c.channel)
		}
	}
	h.log.Debug("session removed", map[string]any{"id": id, "reason": reason})
}

func (h *Hub) clientCount() int {
	h.mu.Lock()
	defer h.mu.Unlock()
	return len(h.clients)
}

func (h *Hub) channelCount() int {
	h.mu.Lock()
	defer h.mu.Unlock()
	return len(h.channels)
}

// send writes one datagram and counts it. A write error is logged and dropped, never fatal.
func (h *Hub) send(data []byte, addr *net.UDPAddr) {
	if h.conn == nil || addr == nil {
		return
	}
	n, err := h.conn.WriteToUDP(data, addr)
	if err != nil {
		h.log.Debug("write failed", map[string]any{"to": addr.String(), "err": err.Error()})
		return
	}
	h.metrics.packetsOut.Add(1)
	h.metrics.bytesOut.Add(int64(n))
}

func (h *Hub) notice(addr *net.UDPAddr, id uint64, reason int32, message string) {
	h.send(BuildNotice(id, reason, message), addr)
}

// DisconnectBanned drops any live session whose address or device is now banned. Called by the
// admin endpoint so a ban takes effect immediately instead of at the next idle timeout.
func (h *Hub) DisconnectBanned(now time.Time) int {
	h.mu.Lock()
	var dropped []uint64
	for id, c := range h.clients {
		if h.bans.Banned(c.ip, c.deviceID, now) {
			dropped = append(dropped, id)
		}
	}
	for _, id := range dropped {
		h.removeLocked(id, "banned")
	}
	h.mu.Unlock()
	if len(dropped) > 0 {
		h.metrics.clients.Store(int64(h.clientCount()))
		h.metrics.channels.Store(int64(h.channelCount()))
		for _, id := range dropped {
			h.log.Info("client disconnected by ban", map[string]any{"id": id})
		}
	}
	return len(dropped)
}

// Snapshot returns a copy of the session table, for tests and diagnostics.
func (h *Hub) Snapshot() []Client {
	h.mu.Lock()
	defer h.mu.Unlock()
	out := make([]Client, 0, len(h.clients))
	for _, c := range h.clients {
		out = append(out, *c)
	}
	return out
}

// ipOf strips the port, so the per-IP cap counts a host rather than each ephemeral socket.
func ipOf(addr *net.UDPAddr) string {
	if addr == nil {
		return ""
	}
	return addr.IP.String()
}

// sanitizeName keeps a display name printable and bounded. A client-supplied string goes into
// logs and other clients' UI, so control characters are dropped here, at the trust boundary.
func sanitizeName(name string) string {
	if len(name) > 32 {
		name = name[:32]
	}
	out := make([]rune, 0, len(name))
	for _, r := range name {
		if r < 0x20 || r == 0x7f {
			continue
		}
		out = append(out, r)
	}
	if len(out) == 0 {
		return "Player"
	}
	return string(out)
}
