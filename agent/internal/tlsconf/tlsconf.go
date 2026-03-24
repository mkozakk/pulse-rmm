package tlsconf

import (
	"crypto"
	"crypto/tls"
	"crypto/x509"
	"encoding/pem"
	"fmt"
	"os"

	"google.golang.org/grpc/credentials"
)

// AgentClientCreds returns gRPC credentials suited to the agent's current state:
//   - cert + CA on disk: mTLS, server cert validated against CA bundle
//   - only CA on disk:   server-validated TLS, no client cert
//   - neither:           InsecureSkipVerify TLS (first-boot TOFU enrol)
//
// The in-memory private key is paired with the on-disk cert because the agent
// never persists the key in plaintext (see internal/secrets).
func AgentClientCreds(certPath, caPath string, priv crypto.PrivateKey) (credentials.TransportCredentials, error) {
	caPEM, err := os.ReadFile(caPath)
	if err != nil && !os.IsNotExist(err) {
		return nil, fmt.Errorf("reading ca: %w", err)
	}

	cfg := &tls.Config{
		MinVersion: tls.VersionTLS13,
		NextProtos: []string{"h2"},
	}
	if err == nil {
		roots := x509.NewCertPool()
		if !roots.AppendCertsFromPEM(caPEM) {
			return nil, fmt.Errorf("ca bundle is not valid PEM")
		}
		cfg.RootCAs = roots
	} else {
		// First-boot TOFU: no CA on disk yet. Skip server verification for the
		// enrol RPC; subsequent connections will use the CA delivered with the cert.
		cfg.InsecureSkipVerify = true
	}

	certPEM, err := os.ReadFile(certPath)
	if err == nil {
		block, _ := pem.Decode(certPEM)
		if block == nil {
			return nil, fmt.Errorf("client cert is not valid PEM")
		}
		x509Cert, err := x509.ParseCertificate(block.Bytes)
		if err != nil {
			return nil, fmt.Errorf("parsing client cert: %w", err)
		}
		tlsCert := tls.Certificate{
			Certificate: [][]byte{block.Bytes},
			PrivateKey:  priv,
			Leaf:        x509Cert,
		}
		cfg.GetClientCertificate = func(_ *tls.CertificateRequestInfo) (*tls.Certificate, error) {
			return &tlsCert, nil
		}
	} else if !os.IsNotExist(err) {
		return nil, fmt.Errorf("reading client cert: %w", err)
	}

	return credentials.NewTLS(cfg), nil
}
