package main

import (
	"net"
	"strings"
	"sync"
	"time"
)

// BanList refuses clients by IP or by device id. Two sources feed it:
//
//   - Static entries from the config, for an operator who wants a permanent block.
//   - Automatic temporary bans, applied when one address racks up repeated authentication
//     failures. A public UDP relay is scanned constantly; without this an attacker gets unlimited
//     password guesses, and the per-IP session cap alone does not slow that down because each
//     guess is a fresh HELLO.
//
// Device-id bans are the interesting half: an IP ban is defeated by a new network, but a device id
// is stable across reconnects, so a banned client cannot simply re-HELLO its way back in.
//
// Everything is in memory. The server still holds no state on disk; a restart clears the temporary
// bans, which is acceptable because the static list is what an operator actually relies on and the
// automatic bans re-accumulate within seconds of an attack resuming.
type BanList struct {
	mu       sync.Mutex
	ips      map[string]struct{}
	prefixes []*net.IPNet
	devices  map[string]struct{}
	// temp holds an automatic ban's expiry by key; a missing key is not banned.
	temp map[string]time.Time
	// failures counts recent auth failures per IP, for the auto-ban threshold.
	failures map[string]int
	// lastFailure is when that count was last bumped, so an old count ages out.
	lastFailure map[string]time.Time

	threshold   int
	banDuration time.Duration
}

// NewBanList builds a list from the static config entries. A malformed CIDR is ignored rather
// than fatal: a typo in one ban should not stop the server from starting.
func NewBanList(staticIPs, staticDevices []string, threshold int, banDuration time.Duration) *BanList {
	b := &BanList{
		ips:         map[string]struct{}{},
		devices:     map[string]struct{}{},
		temp:        map[string]time.Time{},
		failures:    map[string]int{},
		lastFailure: map[string]time.Time{},
		threshold:   threshold,
		banDuration: banDuration,
	}
	for _, entry := range staticIPs {
		entry = strings.TrimSpace(entry)
		if entry == "" {
			continue
		}
		if strings.Contains(entry, "/") {
			if _, network, err := net.ParseCIDR(entry); err == nil {
				b.prefixes = append(b.prefixes, network)
			}
			continue
		}
		if ip := net.ParseIP(entry); ip != nil {
			b.ips[ip.String()] = struct{}{}
		}
	}
	for _, d := range staticDevices {
		if d = strings.TrimSpace(d); d != "" {
			b.devices[d] = struct{}{}
		}
	}
	return b
}

// Banned reports whether an address or device id is currently refused. Either a static entry or a
// live temporary ban counts. An empty device id is never matched against the device list, so a
// client that sends no device id cannot accidentally match an empty ban entry.
func (b *BanList) Banned(ip, deviceID string, now time.Time) bool {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.bannedLocked(ip, deviceID, now)
}

func (b *BanList) bannedLocked(ip, deviceID string, now time.Time) bool {
	if ip != "" {
		if _, ok := b.ips[ip]; ok {
			return true
		}
		parsed := net.ParseIP(ip)
		for _, network := range b.prefixes {
			if parsed != nil && network.Contains(parsed) {
				return true
			}
		}
		if expiry, ok := b.temp["ip:"+ip]; ok {
			if now.Before(expiry) {
				return true
			}
			delete(b.temp, "ip:"+ip)
		}
	}
	if deviceID != "" {
		if _, ok := b.devices[deviceID]; ok {
			return true
		}
	}
	return false
}

// NoteAuthFailure records a bad password/token from an address and bans it once the threshold is
// crossed within the counting window. Returns true when this call triggered a ban.
func (b *BanList) NoteAuthFailure(ip string, now time.Time) bool {
	if b.threshold <= 0 || ip == "" {
		return false
	}
	b.mu.Lock()
	defer b.mu.Unlock()
	if last, ok := b.lastFailure[ip]; ok && now.Sub(last) > b.banDuration {
		b.failures[ip] = 0
	}
	b.failures[ip]++
	b.lastFailure[ip] = now
	if b.failures[ip] >= b.threshold {
		delete(b.failures, ip)
		b.temp["ip:"+ip] = now.Add(b.banDuration)
		return true
	}
	return false
}

// AddTemp bans an address for the configured duration. Used by the admin endpoint, which can pass
// its own duration by temporarily overriding banDuration through BanFor.
func (b *BanList) AddTemp(ip string, until time.Time) {
	if ip == "" {
		return
	}
	b.mu.Lock()
	defer b.mu.Unlock()
	b.temp["ip:"+ip] = until
}

// BanFor bans an address for a caller-chosen duration, for the admin endpoint.
func (b *BanList) BanFor(ip string, d time.Duration, now time.Time) {
	b.AddTemp(ip, now.Add(d))
}

// AddDevice adds a device-id ban at runtime, for the admin endpoint. Device bans are permanent
// (until removed) because a device id is stable and cheap to keep.
func (b *BanList) AddDevice(device string) {
	if device == "" {
		return
	}
	b.mu.Lock()
	defer b.mu.Unlock()
	b.devices[device] = struct{}{}
}

// RemoveDevice lifts a device-id ban.
func (b *BanList) RemoveDevice(device string) {
	b.mu.Lock()
	defer b.mu.Unlock()
	delete(b.devices, device)
}

// RemoveTemp lifts a temporary IP ban and clears its failure count.
func (b *BanList) RemoveTemp(ip string) {
	if ip == "" {
		return
	}
	b.mu.Lock()
	defer b.mu.Unlock()
	delete(b.temp, "ip:"+ip)
	delete(b.failures, ip)
	delete(b.lastFailure, ip)
}

// Sweep drops expired temporary bans and stale failure counts. Called from the reaper so the maps
// do not grow without bound on a busy relay.
func (b *BanList) Sweep(now time.Time) {
	b.mu.Lock()
	defer b.mu.Unlock()
	for key, expiry := range b.temp {
		if !now.Before(expiry) {
			delete(b.temp, key)
		}
	}
	for ip, last := range b.lastFailure {
		if now.Sub(last) > b.banDuration {
			delete(b.lastFailure, ip)
			delete(b.failures, ip)
		}
	}
}

// TempCount is the number of live temporary bans, for metrics.
func (b *BanList) TempCount(now time.Time) int {
	b.mu.Lock()
	defer b.mu.Unlock()
	count := 0
	for _, expiry := range b.temp {
		if now.Before(expiry) {
			count++
		}
	}
	return count
}
