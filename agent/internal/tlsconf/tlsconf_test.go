package tlsconf

import (
	"crypto/rand"
	"crypto/rsa"
	"os"
	"path/filepath"
	"testing"
)

func newKey(t *testing.T) *rsa.PrivateKey {
	t.Helper()
	k, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatalf("GenerateKey: %v", err)
	}
	return k
}

func TestReturnsTLSWithSkipVerifyWhenCAFileMissing(t *testing.T) {
	priv := newKey(t)
	dir := t.TempDir()
	c, err := AgentClientCreds(filepath.Join(dir, "cert.pem"), filepath.Join(dir, "ca.pem"), priv)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if c.Info().SecurityProtocol != "tls" {
		t.Errorf("missing CA should yield TLS+skipVerify (first-boot TOFU); got %s", c.Info().SecurityProtocol)
	}
}

func TestErrorsOnMalformedCABundle(t *testing.T) {
	priv := newKey(t)
	dir := t.TempDir()
	caPath := filepath.Join(dir, "ca.pem")
	if err := os.WriteFile(caPath, []byte("not a pem"), 0o600); err != nil {
		t.Fatal(err)
	}
	_, err := AgentClientCreds(filepath.Join(dir, "cert.pem"), caPath, priv)
	if err == nil {
		t.Fatal("expected error for malformed CA bundle, got nil")
	}
}
