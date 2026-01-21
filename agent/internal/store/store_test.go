package store

import (
	"crypto/ed25519"
	"os"
	"path/filepath"
	"testing"
)

func TestGenerateAndLoadKey(t *testing.T) {
	tmpDir := t.TempDir()
	oldKeyFile := KeyFile
	oldEndpointFile := EndpointFile
	defer func() {
		KeyFile = oldKeyFile
		EndpointFile = oldEndpointFile
	}()

	KeyFile = filepath.Join(tmpDir, "key.pem")
	EndpointFile = filepath.Join(tmpDir, "endpoint.id")

	key1, err := LoadOrGenerateKey()
	if err != nil {
		t.Fatalf("LoadOrGenerateKey failed: %v", err)
	}

	if len(key1) != ed25519.PrivateKeySize {
		t.Errorf("Generated key has wrong size: %d", len(key1))
	}

	key2, err := LoadOrGenerateKey()
	if err != nil {
		t.Fatalf("LoadOrGenerateKey failed: %v", err)
	}

	if !bytesEqual(key1, key2) {
		t.Error("Re-loaded key differs from generated key")
	}
}

func bytesEqual(a, b []byte) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i] != b[i] {
			return false
		}
	}
	return true
}

func TestGetPublicKey(t *testing.T) {
	tmpDir := t.TempDir()
	oldKeyFile := KeyFile
	defer func() {
		KeyFile = oldKeyFile
	}()

	KeyFile = filepath.Join(tmpDir, "key.pem")

	privKey, err := LoadOrGenerateKey()
	if err != nil {
		t.Fatalf("LoadOrGenerateKey failed: %v", err)
	}

	pubKey := GetPublicKey(privKey)
	if len(pubKey) != ed25519.PublicKeySize {
		t.Errorf("Public key has wrong size: %d", len(pubKey))
	}
}

func TestSaveAndLoadEndpointID(t *testing.T) {
	tmpDir := t.TempDir()
	oldEndpointFile := EndpointFile
	defer func() {
		EndpointFile = oldEndpointFile
	}()

	EndpointFile = filepath.Join(tmpDir, "endpoint.id")

	testID := "550e8400-e29b-41d4-a716-446655440000"

	if err := SaveEndpointID(testID); err != nil {
		t.Fatalf("SaveEndpointID failed: %v", err)
	}

	loaded, err := LoadEndpointID()
	if err != nil {
		t.Fatalf("LoadEndpointID failed: %v", err)
	}

	if loaded != testID {
		t.Errorf("Loaded ID %q, expected %q", loaded, testID)
	}

	// Verify file permissions
	info, err := os.Stat(EndpointFile)
	if err != nil {
		t.Fatalf("Stat failed: %v", err)
	}
	if info.Mode()&0o077 != 0 {
		t.Errorf("File has wrong permissions: %o", info.Mode())
	}
}
