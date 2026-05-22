package store

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"fmt"
	"os"
	"path/filepath"

	"github.com/pulsermm/pulse-rmm/agent/internal/secrets"
)

const rsaKeyBits = 2048

var (
	KeyFile      = "/var/lib/pulse-agent/key.pem"
	SecretsFile  = "/var/lib/pulse-agent/secrets.bin"
	EndpointFile = "/var/lib/pulse-agent/endpoint.id"
	CertFile     = "/var/lib/pulse-agent/cert.pem"
	CABundleFile = "/var/lib/pulse-agent/ca.pem"
)

// Init sets the file paths derived from dataDir. Call once at startup before
// any Load/Save calls. Tests override the vars directly instead.
func Init(dataDir string) {
	KeyFile = dataDir + "/key.pem"
	SecretsFile = dataDir + "/secrets.bin"
	EndpointFile = dataDir + "/endpoint.id"
	CertFile = dataDir + "/cert.pem"
	CABundleFile = dataDir + "/ca.pem"
}

func SaveCert(certPem []byte) error {
	if err := os.MkdirAll(filepath.Dir(CertFile), 0o700); err != nil {
		return fmt.Errorf("creating directory: %w", err)
	}
	return os.WriteFile(CertFile, certPem, 0o600)
}

func SaveCABundle(caPem []byte) error {
	if err := os.MkdirAll(filepath.Dir(CABundleFile), 0o700); err != nil {
		return fmt.Errorf("creating directory: %w", err)
	}
	return os.WriteFile(CABundleFile, caPem, 0o600)
}

func LoadOrGenerateKey() (*rsa.PrivateKey, error) {
	key, err := secrets.MachineKey()
	if err != nil {
		return nil, fmt.Errorf("deriving machine key: %w", err)
	}

	if sealed, err := os.ReadFile(SecretsFile); err == nil {
		plain, err := secrets.Open(sealed, key)
		if err != nil {
			return nil, fmt.Errorf("opening sealed key: %w", err)
		}
		priv, err := parsePrivateKey(plain)
		if err == nil {
			return priv, nil
		}
		// Legacy non-RSA blob (e.g. ed25519 from earlier mTLS branch).
		// Wipe identity so the agent re-enrols with a fresh RSA key.
		zeroAndRemove(SecretsFile)
		_ = os.Remove(EndpointFile)
		_ = os.Remove(CertFile)
		_ = os.Remove(CABundleFile)
	} else if !os.IsNotExist(err) {
		return nil, fmt.Errorf("reading sealed key: %w", err)
	}

	priv, err := rsa.GenerateKey(rand.Reader, rsaKeyBits)
	if err != nil {
		return nil, fmt.Errorf("generating key: %w", err)
	}
	if err := writeSealed(priv, key); err != nil {
		return nil, fmt.Errorf("sealing new key: %w", err)
	}
	return priv, nil
}

func GetPublicKey(privKey *rsa.PrivateKey) []byte {
	der, _ := x509.MarshalPKIXPublicKey(&privKey.PublicKey)
	return der
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

func parsePrivateKey(der []byte) (*rsa.PrivateKey, error) {
	k, err := x509.ParsePKCS8PrivateKey(der)
	if err != nil {
		return nil, fmt.Errorf("parsing pkcs8: %w", err)
	}
	rsaKey, ok := k.(*rsa.PrivateKey)
	if !ok {
		return nil, fmt.Errorf("expected RSA private key, got %T", k)
	}
	return rsaKey, nil
}

func writeSealed(priv *rsa.PrivateKey, key []byte) error {
	if err := os.MkdirAll(filepath.Dir(SecretsFile), 0o700); err != nil {
		return fmt.Errorf("creating directory: %w", err)
	}
	der, err := x509.MarshalPKCS8PrivateKey(priv)
	if err != nil {
		return fmt.Errorf("marshalling pkcs8: %w", err)
	}
	ct, err := secrets.Seal(der, key)
	if err != nil {
		return err
	}
	return os.WriteFile(SecretsFile, ct, 0o600)
}

func zeroAndRemove(path string) {
	if info, err := os.Stat(path); err == nil {
		zeros := make([]byte, info.Size())
		_ = os.WriteFile(path, zeros, 0o600)
	}
	_ = os.Remove(path)
}
