package main

import (
	"sync"
	"time"
)

// RateLimiter is a token bucket over two dimensions: packet rate and byte rate. A voice client
// sends a steady trickle of audio plus a 1 Hz beacon, so a packet-rate cap alone would let one
// client flood the server with large datagrams; the byte cap is what actually bounds bandwidth.
// Both buckets must have a token for a packet to pass.
//
// It is pure (no sockets, no clock beyond an injected one) so the drop behaviour is unit-tested.
type RateLimiter struct {
	mu           sync.Mutex
	pps          float64
	burstPackets float64
	bps          float64
	burstBytes   float64
	tokensP      float64
	tokensB      float64
	last         time.Time
}

// NewRateLimiter builds a limiter. A non-positive pps or bps disables that dimension.
func NewRateLimiter(pps, burstPackets, bps, burstBytes int, now time.Time) *RateLimiter {
	return &RateLimiter{
		pps:          float64(pps),
		burstPackets: float64(burstPackets),
		bps:          float64(bps),
		burstBytes:   float64(burstBytes),
		tokensP:      float64(burstPackets),
		tokensB:      float64(burstBytes),
		last:         now,
	}
}

// Allow reports whether a packet of the given size may pass, consuming tokens if so. A packet
// larger than the whole byte bucket is rejected outright rather than draining the bucket and
// starving the next legitimate frames.
func (r *RateLimiter) Allow(size int, now time.Time) bool {
	r.mu.Lock()
	defer r.mu.Unlock()

	if now.After(r.last) {
		elapsed := now.Sub(r.last).Seconds()
		if r.pps > 0 {
			r.tokensP = min(r.burstPackets, r.tokensP+elapsed*r.pps)
		}
		if r.bps > 0 {
			r.tokensB = min(r.burstBytes, r.tokensB+elapsed*r.bps)
		}
		r.last = now
	}

	if r.pps > 0 {
		if r.tokensP < 1 {
			return false
		}
	}
	if r.bps > 0 {
		if float64(size) > r.burstBytes || r.tokensB < float64(size) {
			return false
		}
	}
	if r.pps > 0 {
		r.tokensP--
	}
	if r.bps > 0 {
		r.tokensB -= float64(size)
	}
	return true
}

func min(a, b float64) float64 {
	if a < b {
		return a
	}
	return b
}
