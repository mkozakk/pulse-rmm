package store

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/pulsermm/pulse-rmm/agent/internal/secrets"
)

func setupTmpStore(t *testing.T) string {
	t.Helper()
	dir := t.TempDir()
	oldKey, oldSecrets, oldEndpoint := KeyFile, SecretsFile, EndpointFile
	oldMachineID, oldFallback := secrets.MachineIDPath, secrets.FallbackMachineIDPath
	t.Cleanup(func() {
		KeyFile = oldKey
		SecretsFile = oldSecrets
		EndpointFile = oldEndpoint
		secrets.MachineIDPath = oldMachineID
		secrets.FallbackMachineIDPath = oldFallback
	})
	KeyFile = filepath.Join(dir, "key.pem")
	SecretsFile = filepath.Join(dir, "secrets.bin")
	EndpointFile = filepath.Join(dir, "endpoint.id")
	secrets.MachineIDPath = filepath.Join(dir, "machine-id")
	secrets.FallbackMachineIDPath = filepath.Join(dir, "fallback.id")
	os.WriteFile(secrets.MachineIDPath, []byte("test-machine"), 0o600)
	return dir
}

func TestGenerateAndLoadKey(t *testing.T) {
	setupTmpStore(t)

	key1, err := LoadOrGenerateKey()
	if err != nil {
		t.Fatalf("LoadOrGenerateKey failed: %v", err)
	}

	if key1.N.BitLen() != rsaKeyBits {
		t.Errorf("Generated key has wrong size: %d bits", key1.N.BitLen())
	}

	key2, err := LoadOrGenerateKey()
	if err != nil {
		t.Fatalf("LoadOrGenerateKey failed: %v", err)
	}

	if key1.N.Cmp(key2.N) != 0 {
		t.Error("Re-loaded key differs from generated key")
	}
}

func TestGetPublicKey(t *testing.T) {
	setupTmpStore(t)

	privKey, err := LoadOrGenerateKey()
	if err != nil {
		t.Fatalf("LoadOrGenerateKey failed: %v", err)
	}

	pubKey := GetPublicKey(privKey)
	if len(pubKey) == 0 {
		t.Error("Public key DER is empty")
	}
}

func TestGeneratedKeyIsSealedNotPlaintext(t *testing.T) {
	setupTmpStore(t)

	if _, err := LoadOrGenerateKey(); err != nil {
		t.Fatalf("LoadOrGenerateKey: %v", err)
	}

	if _, err := os.Stat(KeyFile); !os.IsNotExist(err) {
		t.Errorf("plaintext key.pem must not exist after generation, stat err=%v", err)
	}
	info, err := os.Stat(SecretsFile)
	if err != nil {
		t.Fatalf("secrets.bin missing: %v", err)
	}
	if info.Mode()&0o077 != 0 {
		t.Errorf("secrets.bin permissions too open: %o", info.Mode())
	}
}

func TestLegacyBlobWipedAndRegenerated(t *testing.T) {
	setupTmpStore(t)

	mk, err := secrets.MachineKey()
	if err != nil {
		t.Fatalf("MachineKey: %v", err)
	}
	junk := []byte("not-a-pkcs8-der-blob")
	ct, err := secrets.Seal(junk, mk)
	if err != nil {
		t.Fatalf("Seal: %v", err)
	}
	if err := os.WriteFile(SecretsFile, ct, 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(EndpointFile, []byte("old-id"), 0o600); err != nil {
		t.Fatal(err)
	}

	priv, err := LoadOrGenerateKey()
	if err != nil {
		t.Fatalf("LoadOrGenerateKey: %v", err)
	}
	if priv.N.BitLen() != rsaKeyBits {
		t.Errorf("expected fresh %d-bit RSA key, got %d bits", rsaKeyBits, priv.N.BitLen())
	}
	if _, err := os.Stat(EndpointFile); !os.IsNotExist(err) {
		t.Error("endpoint.id should be wiped after legacy-blob detection")
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

	info, err := os.Stat(EndpointFile)
	if err != nil {
		t.Fatalf("Stat failed: %v", err)
	}
	if info.Mode()&0o077 != 0 {
		t.Errorf("File has wrong permissions: %o", info.Mode())
	}
}
