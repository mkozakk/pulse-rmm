package config

import (
	"fmt"
	"net/url"
	"os"
	"runtime"

	"gopkg.in/yaml.v3"
)

type Config struct {
	APIURL         string `yaml:"api_url"`
	GRPCAddr       string `yaml:"grpc_addr"`
	EnrolmentToken string `yaml:"enrolment_token,omitempty"`
	DataDir        string `yaml:"data_dir"`
	LogLevel       string `yaml:"log_level"`

	// path this config was loaded from, for Save()
	path string
}

func DefaultPath() string {
	if p := os.Getenv("PULSE_CONFIG"); p != "" {
		return p
	}
	if runtime.GOOS == "windows" {
		programData := os.Getenv("ProgramData")
		if programData == "" {
			programData = `C:\ProgramData`
		}
		return programData + `\pulse-agent\config.yaml`
	}
	return "/etc/pulse-agent/config.yaml"
}

func Load(path string) (*Config, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("reading config: %w", err)
	}

	var cfg Config
	if err := yaml.Unmarshal(data, &cfg); err != nil {
		return nil, fmt.Errorf("parsing config: %w", err)
	}

	if cfg.APIURL == "" {
		return nil, fmt.Errorf("api_url is required in %s", path)
	}

	// If grpc_addr not explicitly set, derive from api_url
	if cfg.GRPCAddr == "" {
		derived, err := deriveGRPCAddr(cfg.APIURL)
		if err != nil {
			return nil, fmt.Errorf("deriving grpc_addr from api_url: %w", err)
		}
		cfg.GRPCAddr = derived
	}

	if cfg.DataDir == "" {
		if runtime.GOOS == "windows" {
			programData := os.Getenv("ProgramData")
			if programData == "" {
				programData = `C:\ProgramData`
			}
			cfg.DataDir = programData + `\pulse-agent`
		} else {
			cfg.DataDir = "/var/lib/pulse-agent"
		}
	}

	if cfg.LogLevel == "" {
		cfg.LogLevel = "info"
	}

	cfg.path = path
	return &cfg, nil
}

// deriveGRPCAddr extracts hostname from api_url and appends :9090
// Examples:
//   https://pulse.example.com → pulse.example.com:9090
//   https://192.168.1.100:8080 → 192.168.1.100:9090
//   http://localhost:8080 → localhost:9090
func deriveGRPCAddr(apiURL string) (string, error) {
	u, err := url.Parse(apiURL)
	if err != nil {
		return "", fmt.Errorf("invalid api_url: %w", err)
	}

	host := u.Hostname()
	if host == "" {
		return "", fmt.Errorf("api_url has no hostname: %s", apiURL)
	}

	// Always use port 9090 for gRPC, regardless of api_url port
	return host + ":9090", nil
}

// RemoveToken rewrites the config file with the enrolment_token field omitted.
// Called after successful enrolment so the token is not left on disk.
func (c *Config) RemoveToken() error {
	if c.path == "" {
		return fmt.Errorf("config path unknown; cannot save")
	}
	c.EnrolmentToken = ""
	return c.save()
}

func (c *Config) save() error {
	data, err := yaml.Marshal(c)
	if err != nil {
		return fmt.Errorf("marshalling config: %w", err)
	}
	if err := os.WriteFile(c.path, data, 0600); err != nil {
		return fmt.Errorf("writing config: %w", err)
	}
	return nil
}
