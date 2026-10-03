package main

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"errors"
	"strconv"
	"strings"
	"time"
)

// Tokens are the relay's answer to "a shared password on an open relay is not enough". The server
// holds one secret; a launcher user is given that secret once, and derives a short-lived, signed
// join token from it locally. The server verifies the signature and the expiry, so:
//
//   - the secret never travels on the wire (only the derived token does),
//   - a captured token is useless once it expires,
//   - a token is bound to a device id, so one player cannot present another's.
//
// The token is not a single-use nonce: this is UDP, with no session to spend it against, and a
// reconnect must not require the server to remember every token it has seen. The expiry window is
// what bounds replay, which is why the TTL is a deployment setting rather than a constant.
//
// Format: v1.<base64url(deviceID|expiryUnix)>.<base64url(hmacSHA256(secret, "v1|" + payload))>.
// The signature covers the version prefix, so a future format cannot be confused with this one.

const tokenVersion = "v1"

// maxTokenTTL bounds how far in the future a token may claim to expire. Anyone holding the secret
// could mint a decade-long token; capping it here means a leaked secret still ages out.
const maxTokenTTL = 30 * 24 * time.Hour

// maxDeviceID bounds the device id that goes into the token, mirroring the string cap on the wire.
const maxDeviceID = 64

var (
	ErrTokenMalformed = errors.New("malformed token")
	ErrTokenExpired   = errors.New("token expired")
	ErrTokenSignature = errors.New("bad token signature")
	ErrTokenTooLong   = errors.New("token expiry too far in the future")
)

// IssueToken mints a token for a device. Exposed so the deployment can print one for a user
// (`voice-relay -mint-token <device>`), and so tests can drive the verifier with real tokens.
func IssueToken(secret []byte, deviceID string, ttl time.Duration, now time.Time) string {
	if ttl <= 0 || ttl > maxTokenTTL {
		ttl = maxTokenTTL
	}
	return signToken(secret, sanitizeDeviceID(deviceID), now.Add(ttl).Unix())
}

func signToken(secret []byte, deviceID string, expiry int64) string {
	payload := deviceID + "|" + strconv.FormatInt(expiry, 10)
	mac := hmac.New(sha256.New, secret)
	mac.Write([]byte(tokenVersion + "|" + payload))
	return tokenVersion + "." +
		base64.RawURLEncoding.EncodeToString([]byte(payload)) + "." +
		base64.RawURLEncoding.EncodeToString(mac.Sum(nil))
}

// VerifyToken checks a token and returns the device id it was issued to. Every failure mode is a
// distinct error so the caller can log why without echoing the token itself.
func VerifyToken(secret []byte, token string, now time.Time) (string, error) {
	if len(secret) == 0 {
		return "", ErrTokenMalformed
	}
	token = strings.TrimSpace(token)
	parts := strings.Split(token, ".")
	if len(parts) != 3 || parts[0] != tokenVersion {
		return "", ErrTokenMalformed
	}
	payloadBytes, err := base64.RawURLEncoding.DecodeString(parts[1])
	if err != nil {
		return "", ErrTokenMalformed
	}
	signature, err := base64.RawURLEncoding.DecodeString(parts[2])
	if err != nil {
		return "", ErrTokenMalformed
	}

	// Constant-time compare: a byte-by-byte `==` leaks how much of a guessed signature is right.
	mac := hmac.New(sha256.New, secret)
	mac.Write([]byte(tokenVersion + "|" + string(payloadBytes)))
	if !hmac.Equal(signature, mac.Sum(nil)) {
		return "", ErrTokenSignature
	}

	payload := string(payloadBytes)
	sep := strings.LastIndexByte(payload, '|')
	if sep <= 0 {
		return "", ErrTokenMalformed
	}
	deviceID := payload[:sep]
	expiry, err := strconv.ParseInt(payload[sep+1:], 10, 64)
	if err != nil || deviceID == "" || len(deviceID) > maxDeviceID {
		return "", ErrTokenMalformed
	}
	if expiry < now.Unix() {
		return "", ErrTokenExpired
	}
	if expiry > now.Add(maxTokenTTL).Unix() {
		return "", ErrTokenTooLong
	}
	return deviceID, nil
}

// sanitizeDeviceID keeps the id printable and bounded; it becomes part of a signed payload, so a
// separator or control byte here would make the format ambiguous.
func sanitizeDeviceID(id string) string {
	id = strings.TrimSpace(id)
	out := make([]rune, 0, len(id))
	for _, r := range id {
		if r < 0x21 || r == '|' || r == 0x7f {
			continue
		}
		out = append(out, r)
	}
	if len(out) == 0 {
		return "device"
	}
	if len(out) > maxDeviceID {
		out = out[:maxDeviceID]
	}
	return string(out)
}
