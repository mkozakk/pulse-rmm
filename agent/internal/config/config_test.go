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

func writeTemp(t *testing.T, content string) string {
	t.Helper()
	dir := t.TempDir()
	p := filepath.Join(dir, "config.yaml")
	if err := os.WriteFile(p, []byte(content), 0600); err != nil {
		t.Fatalf("writing temp config: %v", err)
	}
	return p
}
