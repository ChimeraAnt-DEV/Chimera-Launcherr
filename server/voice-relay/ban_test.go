package main

import (
	"testing"
	"time"
)

func TestBanListStaticEntries(t *testing.T) {
	b := NewBanList([]string{"203.0.113.5", "198.51.100.0/24"}, []string{"bad-device"}, 0, time.Minute)
	now := time.Now()

	if !b.Banned("203.0.113.5", "", now) {
		t.Error("static ip not banned")
	}
	if !b.Banned("198.51.100.7", "", now) {
		t.Error("ip inside static CIDR not banned")
	}
	if b.Banned("203.0.113.6", "", now) {
		t.Error("unlisted ip banned")
	}
	if !b.Banned("1.2.3.4", "bad-device", now) {
		t.Error("static device not banned")
	}
	if b.Banned("1.2.3.4", "", now) {
		t.Error("empty device matched the device list")
	}
}

func TestAutoBanAfterRepeatedFailures(t *testing.T) {
	b := NewBanList(nil, nil, 3, time.Minute)
	now := time.Now()
	ip := "203.0.113.9"

	if b.NoteAuthFailure(ip, now) {
		t.Error("banned before the threshold")
	}
	if b.NoteAuthFailure(ip, now.Add(time.Second)) {
		t.Error("banned before the threshold")
	}
	if !b.NoteAuthFailure(ip, now.Add(2*time.Second)) {
		t.Error("not banned at the threshold")
	}
	if !b.Banned(ip, "", now.Add(3*time.Second)) {
		t.Error("ip not banned after crossing the threshold")
	}
	// The ban expires after its window.
	if b.Banned(ip, "", now.Add(2*time.Minute)) {
		t.Error("ban outlived its window")
	}
}

func TestFailureCountAgesOut(t *testing.T) {
	b := NewBanList(nil, nil, 3, time.Minute)
	now := time.Now()
	ip := "203.0.113.10"
	b.NoteAuthFailure(ip, now)
	b.NoteAuthFailure(ip, now)
	// A failure far outside the window resets the count, so scattered failures never ban.
	if b.NoteAuthFailure(ip, now.Add(5*time.Minute)) {
		t.Error("scattered failures accumulated into a ban")
	}
}

func TestDisableAutoBan(t *testing.T) {
	b := NewBanList(nil, nil, 0, time.Minute)
	now := time.Now()
	for i := 0; i < 100; i++ {
		if b.NoteAuthFailure("1.2.3.4", now) {
			t.Fatal("auto-ban fired while disabled")
		}
	}
	if b.Banned("1.2.3.4", "", now) {
		t.Error("ip banned while auto-ban disabled")
	}
}

func TestAddAndRemoveRuntimeBans(t *testing.T) {
	b := NewBanList(nil, nil, 0, time.Minute)
	now := time.Now()

	b.BanFor("203.0.113.20", time.Minute, now)
	if !b.Banned("203.0.113.20", "", now) {
		t.Error("runtime ip ban not applied")
	}
	b.RemoveTemp("203.0.113.20")
	if b.Banned("203.0.113.20", "", now) {
		t.Error("runtime ip ban not lifted")
	}

	b.AddDevice("dev-1")
	if !b.Banned("", "dev-1", now) {
		t.Error("runtime device ban not applied")
	}
	b.RemoveDevice("dev-1")
	if b.Banned("", "dev-1", now) {
		t.Error("runtime device ban not lifted")
	}
}

func TestSweepDropsExpiredBans(t *testing.T) {
	b := NewBanList(nil, nil, 0, time.Minute)
	now := time.Now()
	b.BanFor("203.0.113.30", time.Minute, now)
	b.NoteAuthFailure("203.0.113.31", now)

	b.Sweep(now.Add(2 * time.Minute))
	if b.TempCount(now.Add(2*time.Minute)) != 0 {
		t.Error("expired temp ban survived the sweep")
	}
	if b.Banned("203.0.113.31", "", now.Add(2*time.Minute)) {
		t.Error("stale failure state outlived the sweep")
	}
}
