package enrolment

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"encoding/pem"
	"testing"
)

func TestBuildCsrIsValidPemSignedByPrivateKey(t *testing.T) {
	priv, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatalf("GenerateKey: %v", err)
	}
	csrPem, err := buildCsr(priv, "test-host")
	if err != nil {
		t.Fatalf("buildCsr: %v", err)
	}
	block, _ := pem.Decode(csrPem)
	if block == nil || block.Type != "CERTIFICATE REQUEST" {
		t.Fatalf("not a CSR pem block: %v", block)
	}
	csr, err := x509.ParseCertificateRequest(block.Bytes)
	if err != nil {
		t.Fatalf("ParseCertificateRequest: %v", err)
	}
	if err := csr.CheckSignature(); err != nil {
		t.Fatalf("CSR signature invalid: %v", err)
	}
	pub, ok := csr.PublicKey.(*rsa.PublicKey)
	if !ok {
		t.Fatalf("CSR public key is not RSA: %T", csr.PublicKey)
	}
	if pub.N.Cmp(priv.N) != 0 {
		t.Error("CSR public key does not match agent private key")
	}
}
