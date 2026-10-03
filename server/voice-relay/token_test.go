package main

import (
	"strings"
	"testing"
	"time"
)

func TestTokenRoundTrip(t *testing.T) {
	secret := []byte("correct horse battery staple")
	now := time.Unix(1_700_000_000, 0)
	token := IssueToken(secret, "device-123", time.Hour, now)

	device, err := VerifyToken(secret, token, now.Add(30*time.Minute))
	if err != nil {
		t.Fatalf("verify failed: %v", err)
	}
	if device != "device-123" {
		t.Errorf("device = %q, want device-123", device)
	}
}

// TestTokenGoldenVectorMatchesTheLauncher pins the exact string the Java VoiceTokenTest asserts,
// so a format change on either side fails one suite rather than shipping a relay that rejects
// every client. Keep this literal and VoiceTokenTest.GOLDEN in lockstep.
func TestTokenGoldenVectorMatchesTheLauncher(t *testing.T) {
	secret := []byte("correct horse battery staple")
	now := time.Unix(1_700_000_000, 0)
	const golden = "v1.ZGV2aWNlLTEyM3wxNzAwMDIxNjAw.MUlxqkbsc9MPGxhmOc1LfqroTszlz8iTUeustnpABHI"
	if got := IssueToken(secret, "device-123", 6*time.Hour, now); got != golden {
		t.Errorf("IssueToken = %q, want golden %q", got, golden)
	}
	device, err := VerifyToken(secret, golden, now)
	if err != nil || device != "device-123" {
		t.Errorf("VerifyToken(golden) = (%q, %v), want (device-123, nil)", device, err)
	}
}

func TestTokenRejectsExpired(t *testing.T) {
	secret := []byte("s")
	now := time.Unix(1_700_000_000, 0)
	token := IssueToken(secret, "d", time.Minute, now)
	if _, err := VerifyToken(secret, token, now.Add(2*time.Minute)); err != ErrTokenExpired {
		t.Errorf("err = %v, want ErrTokenExpired", err)
	}
}

func TestTokenRejectsWrongSecret(t *testing.T) {
	now := time.Unix(1_700_000_000, 0)
	token := IssueToken([]byte("right"), "d", time.Hour, now)
	if _, err := VerifyToken([]byte("wrong"), token, now); err != ErrTokenSignature {
		t.Errorf("err = %v, want ErrTokenSignature", err)
	}
}

func TestTokenRejectsTamperedPayload(t *testing.T) {
	secret := []byte("s")
	now := time.Unix(1_700_000_000, 0)
	token := IssueToken(secret, "device", time.Hour, now)
	// Flip the device id in the payload without re-signing; the signature must no longer match.
	parts := strings.Split(token, ".")
	tampered := parts[0] + "." + parts[1][:len(parts[1])-1] + "X" + "." + parts[2]
	if _, err := VerifyToken(secret, tampered, now); err == nil {
		t.Error("tampered token verified, want rejection")
	}
}

func TestTokenRejectsMalformed(t *testing.T) {
	secret := []byte("s")
	now := time.Unix(1_700_000_000, 0)
	for _, bad := range []string{"", "nope", "v1.only-two-parts", "v9.aaaa.bbbb", "v1.!!!.???"} {
		if _, err := VerifyToken(secret, bad, now); err == nil {
			t.Errorf("VerifyToken(%q) succeeded, want error", bad)
		}
	}
}

func TestTokenRejectsFarFutureExpiry(t *testing.T) {
	// A hand-crafted token signing an expiry a decade out must be refused, so a leaked secret
	// still ages out.
	secret := []byte("s")
	now := time.Unix(1_700_000_000, 0)
	long := signToken(secret, "d", now.Add(10*365*24*time.Hour).Unix())
	if _, err := VerifyToken(secret, long, now); err != ErrTokenTooLong {
		t.Errorf("err = %v, want ErrTokenTooLong", err)
	}
}

func TestTokenDeviceIdIsSanitized(t *testing.T) {
	secret := []byte("s")
	now := time.Unix(1_700_000_000, 0)
	// A pipe or control character in the device id must not break the payload format.
	token := IssueToken(secret, "a|b\tc", time.Hour, now)
	device, err := VerifyToken(secret, token, now)
	if err != nil {
		t.Fatalf("verify failed: %v", err)
	}
	if strings.ContainsAny(device, "|\t") {
		t.Errorf("device = %q, still contains a separator", device)
	}
}

func TestIssueTokenCapsTTL(t *testing.T) {
	secret := []byte("s")
	now := time.Unix(1_700_000_000, 0)
	token := IssueToken(secret, "d", 100*365*24*time.Hour, now)
	device, err := VerifyToken(secret, token, now)
	if err != nil {
		t.Fatalf("verify failed: %v", err)
	}
	if device != "d" {
		t.Errorf("device = %q, want d", device)
	}
}
