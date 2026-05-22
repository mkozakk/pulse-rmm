package enrolment

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"math/big"
	"testing"
	"time"
)

func issueCert(t *testing.T, notBefore, notAfter time.Time) []byte {
	t.Helper()
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		t.Fatalf("key: %v", err)
	}
	tmpl := &x509.Certificate{
		SerialNumber: big.NewInt(1),
		Subject:      pkix.Name{CommonName: "test"},
		NotBefore:    notBefore,
		NotAfter:     notAfter,
	}
	der, err := x509.CreateCertificate(rand.Reader, tmpl, tmpl, &key.PublicKey, key)
	if err != nil {
		t.Fatalf("cert: %v", err)
	}
	return pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der})
}

func TestNextRenewAt_HalfTtl(t *testing.T) {
	start := time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC)
	pem := issueCert(t, start, start.Add(24*time.Hour))
	got, err := NextRenewAt(pem, start)
	if err != nil {
		t.Fatalf("NextRenewAt: %v", err)
	}
	want := start.Add(12 * time.Hour)
	if !got.Equal(want) {
		t.Errorf("expected renewal at %s, got %s", want, got)
	}
}

func TestNextRenewAt_AlreadyPast_ReturnsNow(t *testing.T) {
	start := time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC)
	pemBytes := issueCert(t, start, start.Add(24*time.Hour))
	now := start.Add(20 * time.Hour) // past 50% TTL
	got, err := NextRenewAt(pemBytes, now)
	if err != nil {
		t.Fatalf("NextRenewAt: %v", err)
	}
	if !got.Equal(now) {
		t.Errorf("expected immediate renewal at %s, got %s", now, got)
	}
}

func TestNextRenewAt_RejectsGarbage(t *testing.T) {
	_, err := NextRenewAt([]byte("not a pem"), time.Now())
	if err == nil {
		t.Errorf("expected error for non-PEM input")
	}
}
