package store

import (
	"crypto/ed25519"
	"encoding/pem"
	"fmt"
	"os"
	"path/filepath"
)

var (
	KeyFile      = "/var/lib/pulse-agent/key.pem"
	EndpointFile = "/var/lib/pulse-agent/endpoint.id"
)

// Init sets the file paths derived from dataDir. Call once at startup before
// any Load/Save calls. Tests override the vars directly instead.
func Init(dataDir string) {
	KeyFile = dataDir + "/key.pem"
	EndpointFile = dataDir + "/endpoint.id"
}

func LoadOrGenerateKey() (ed25519.PrivateKey, error) {
	keyBytes, err := os.ReadFile(KeyFile)
	if err == nil {
		return parsePrivateKey(keyBytes)
	}

	if !os.IsNotExist(err) {
		return nil, fmt.Errorf("reading key: %w", err)
	}

	pubKey, privKey, err := ed25519.GenerateKey(nil)
	if err != nil {
		return nil, fmt.Errorf("generating key: %w", err)
	}

	if err := savePrivateKey(privKey); err != nil {
		return nil, fmt.Errorf("saving key: %w", err)
	}

	fmt.Printf("Generated new endpoint key (public: %x)\n", pubKey)
	return privKey, nil
}

func GetPublicKey(privKey ed25519.PrivateKey) []byte {
	return privKey.Public().(ed25519.PublicKey)
}

func SaveEndpointID(endpointID string) error {
	if err := os.MkdirAll(filepath.Dir(EndpointFile), 0700); err != nil {
		return fmt.Errorf("creating directory: %w", err)
	}

	if err := os.WriteFile(EndpointFile, []byte(endpointID), 0600); err != nil {
		return fmt.Errorf("writing endpoint file: %w", err)
	}

	return nil
}

func LoadEndpointID() (string, error) {
	data, err := os.ReadFile(EndpointFile)
	if err != nil {
		return "", fmt.Errorf("reading endpoint file: %w", err)
	}
	return string(data), nil
}

func parsePrivateKey(keyBytes []byte) (ed25519.PrivateKey, error) {
	block, _ := pem.Decode(keyBytes)
	if block == nil {
		return nil, fmt.Errorf("invalid PEM format")
	}

	privKey := ed25519.PrivateKey(block.Bytes)
	if len(privKey) != ed25519.PrivateKeySize {
		return nil, fmt.Errorf("invalid key size: %d", len(privKey))
	}

	return privKey, nil
}

func savePrivateKey(privKey ed25519.PrivateKey) error {
	if err := os.MkdirAll(filepath.Dir(KeyFile), 0700); err != nil {
		return fmt.Errorf("creating directory: %w", err)
	}

	block := &pem.Block{
		Type:  "PRIVATE KEY",
		Bytes: privKey,
	}

	keyFile, err := os.OpenFile(KeyFile, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0600)
	if err != nil {
		return fmt.Errorf("opening file: %w", err)
	}
	defer keyFile.Close()

	if err := pem.Encode(keyFile, block); err != nil {
		return fmt.Errorf("encoding PEM: %w", err)
	}

	return nil
}
