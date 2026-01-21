package enrolment

import (
	"crypto/ed25519"
	"testing"
)

func TestKeyGeneration(t *testing.T) {
	pubKey, privKey, err := ed25519.GenerateKey(nil)
	if err != nil {
		t.Fatalf("GenerateKey failed: %v", err)
	}

	if len(privKey) != ed25519.PrivateKeySize {
		t.Errorf("Private key size is %d, expected %d", len(privKey), ed25519.PrivateKeySize)
	}

	if len(pubKey) != ed25519.PublicKeySize {
		t.Errorf("Public key size is %d, expected %d", len(pubKey), ed25519.PublicKeySize)
	}

	// Verify key types
	extractedPub := privKey.Public().(ed25519.PublicKey)
	if len(extractedPub) != ed25519.PublicKeySize {
		t.Errorf("Extracted public key size is %d, expected %d", len(extractedPub), ed25519.PublicKeySize)
	}
}
