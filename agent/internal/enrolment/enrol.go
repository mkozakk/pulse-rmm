package enrolment

import (
	"context"
	"crypto"
	"crypto/rand"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"fmt"
	"os"
	"runtime"

	pb "github.com/pulsermm/pulse-rmm/agent/gen/pulse/v1"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials"
)

type Result struct {
	EndpointID string
	CertPEM    []byte
	CABundle   []byte
}

func Enrol(ctx context.Context, token string, grpcAddr string, apiURL string, privKey crypto.Signer, creds credentials.TransportCredentials) (Result, error) {
	conn, err := grpc.NewClient(grpcAddr, grpc.WithTransportCredentials(creds))
	if err != nil {
		return Result{}, fmt.Errorf("dialing grpc: %w", err)
	}
	defer conn.Close()

	pubKey, err := x509.MarshalPKIXPublicKey(privKey.Public())
	if err != nil {
		return Result{}, fmt.Errorf("marshalling public key: %w", err)
	}
	client := pb.NewAgentServiceClient(conn)

	hostname, err := os.Hostname()
	if err != nil {
		return Result{}, fmt.Errorf("getting hostname: %w", err)
	}

	csrPem, err := buildCsr(privKey, hostname)
	if err != nil {
		return Result{}, fmt.Errorf("building csr: %w", err)
	}

	resp, err := client.Enrol(ctx, &pb.EnrolRequest{
		Token:     token,
		PublicKey: pubKey,
		Hostname:  hostname,
		Os:        runtime.GOOS,
		Arch:      runtime.GOARCH,
		CsrPem:    string(csrPem),
	})
	if err != nil {
		return Result{}, fmt.Errorf("enrol rpc: %w", err)
	}

	return Result{
		EndpointID: resp.EndpointId,
		CertPEM:    resp.ClientCertPem,
		CABundle:   resp.CaCertPem,
	}, nil
}

func buildCsr(priv crypto.Signer, hostname string) ([]byte, error) {
	tmpl := &x509.CertificateRequest{
		Subject:            pkix.Name{CommonName: "pending-" + hostname},
		SignatureAlgorithm: x509.SHA256WithRSA,
	}
	der, err := x509.CreateCertificateRequest(rand.Reader, tmpl, priv)
	if err != nil {
		return nil, fmt.Errorf("creating csr: %w", err)
	}
	return pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE REQUEST", Bytes: der}), nil
}
