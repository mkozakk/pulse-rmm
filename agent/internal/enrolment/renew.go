package enrolment

import (
	"context"
	"crypto"
	"crypto/x509"
	"encoding/pem"
	"fmt"
	"os"
	"time"

	pb "github.com/pulsermm/pulse-rmm/agent/gen/pulse/v1"
)

// RenewCert builds a fresh CSR from privKey and asks the gateway to issue a
// new short-lived client certificate. The returned PEM blobs are caller's
// responsibility to persist.
func RenewCert(ctx context.Context, client pb.AgentServiceClient, privKey crypto.Signer) (certPem, caPem []byte, err error) {
	hostname, _ := os.Hostname()
	csrPem, err := buildCsr(privKey, hostname)
	if err != nil {
		return nil, nil, fmt.Errorf("building csr: %w", err)
	}
	resp, err := client.RenewCert(ctx, &pb.RenewCertRequest{CsrPem: string(csrPem)})
	if err != nil {
		return nil, nil, fmt.Errorf("renew rpc: %w", err)
	}
	return resp.ClientCertPem, resp.CaCertPem, nil
}

// NextRenewAt returns the wall-clock instant at which a renewal should be
// triggered: 50% of the cert's TTL. If the cert is already past that point,
// returns now (caller should renew immediately).
func NextRenewAt(certPem []byte, now time.Time) (time.Time, error) {
	block, _ := pem.Decode(certPem)
	if block == nil {
		return time.Time{}, fmt.Errorf("not a valid PEM cert")
	}
	cert, err := x509.ParseCertificate(block.Bytes)
	if err != nil {
		return time.Time{}, fmt.Errorf("parsing cert: %w", err)
	}
	ttl := cert.NotAfter.Sub(cert.NotBefore)
	renewAt := cert.NotBefore.Add(ttl / 2)
	if renewAt.Before(now) {
		return now, nil
	}
	return renewAt, nil
}
