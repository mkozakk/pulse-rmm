package config

import (
	"os"
	"path/filepath"
	"testing"
)

func TestLoad_happy(t *testing.T) {
	f := writeTemp(t, `
api_url: https://pulse.example.com
grpc_addr: pulse.example.com:9090
enrolment_token: tok123
data_dir: /tmp/pulse
log_level: debug
`)
	cfg, err := Load(f)
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}
	if cfg.APIURL != "https://pulse.example.com" {
		t.Errorf("api_url = %q", cfg.APIURL)
	}
	if cfg.EnrolmentToken != "tok123" {
		t.Errorf("enrolment_token = %q", cfg.EnrolmentToken)
	}
	if cfg.DataDir != "/tmp/pulse" {
		t.Errorf("data_dir = %q", cfg.DataDir)
	}
}

func TestLoad_missing_api_url(t *testing.T) {
	f := writeTemp(t, "data_dir: /tmp/pulse\n")
	_, err := Load(f)
	if err == nil {
		t.Fatal("expected error for missing api_url")
	}
}

func TestLoad_defaults_data_dir(t *testing.T) {
	f := writeTemp(t, "api_url: https://pulse.example.com\n")
	cfg, err := Load(f)
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}
	if cfg.DataDir == "" {
		t.Error("data_dir should default to a non-empty path")
	}
}

func TestRemoveToken(t *testing.T) {
	f := writeTemp(t, `
api_url: https://pulse.example.com
enrolment_token: tok123
data_dir: /tmp/pulse
`)
	cfg, err := Load(f)
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}

	if err := cfg.RemoveToken(); err != nil {
		t.Fatalf("RemoveToken failed: %v", err)
	}

	// reload and check token is gone
	cfg2, err := Load(f)
	if err != nil {
		t.Fatalf("Load after RemoveToken failed: %v", err)
	}
	if cfg2.EnrolmentToken != "" {
		t.Errorf("token still present after RemoveToken: %q", cfg2.EnrolmentToken)
	}
	if cfg2.APIURL != "https://pulse.example.com" {
		t.Errorf("api_url changed after RemoveToken: %q", cfg2.APIURL)
	}
}

func TestDeriveGRPCAddr(t *testing.T) {
	tests := []struct {
		apiURL   string
		expected string
		wantErr  bool
	}{
		{"https://pulse.example.com", "pulse.example.com:9090", false},
		{"https://pulse.example.com:8080", "pulse.example.com:9090", false},
		{"http://localhost:8080", "localhost:9090", false},
		{"https://192.168.1.100", "192.168.1.100:9090", false},
		{"https://192.168.1.100:8080", "192.168.1.100:9090", false},
	}

	for _, tt := range tests {
		t.Run(tt.apiURL, func(t *testing.T) {
			got, err := deriveGRPCAddr(tt.apiURL)
			if (err != nil) != tt.wantErr {
				t.Errorf("deriveGRPCAddr(%q) error = %v, wantErr %v", tt.apiURL, err, tt.wantErr)
				return
			}
			if got != tt.expected {
				t.Errorf("deriveGRPCAddr(%q) = %q, want %q", tt.apiURL, got, tt.expected)
			}
		})
	}
}

func TestLoad_autoderive_grpc_addr(t *testing.T) {
	// No explicit grpc_addr in config; should derive from api_url
	f := writeTemp(t, `
api_url: https://192.168.1.50:8080
data_dir: /tmp/test
`)
	cfg, err := Load(f)
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}

	// Should derive: 192.168.1.50:9090
	if cfg.GRPCAddr != "192.168.1.50:9090" {
		t.Errorf("expected grpc_addr to be derived as 192.168.1.50:9090, got %s", cfg.GRPCAddr)
	}
}

func TestLoad_explicit_grpc_addr_precedence(t *testing.T) {
	// Explicit grpc_addr should override derivation
	f := writeTemp(t, `
api_url: https://pulse.example.com:8080
grpc_addr: custom-host.local:9090
data_dir: /tmp/test
`)
	cfg, err := Load(f)
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}

	if cfg.GRPCAddr != "custom-host.local:9090" {
		t.Errorf("expected grpc_addr to be custom-host.local:9090, got %s", cfg.GRPCAddr)
	}
}

func writeTemp(t *testing.T, content string) string {
	t.Helper()
	dir := t.TempDir()
	p := filepath.Join(dir, "config.yaml")
	if err := os.WriteFile(p, []byte(content), 0600); err != nil {
		t.Fatalf("writing temp config: %v", err)
	}
	return p
}
