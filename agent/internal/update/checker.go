package update

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"runtime"
	"strings"
	"time"
)

// UpdateInfo holds what the backend returned when a newer version exists.
type UpdateInfo struct {
	Version     string
	DownloadURL string
	SHA256      string
	SizeBytes   int64
}

type checkResponse struct {
	UpToDate    bool   `json:"upToDate"`
	Version     string `json:"version"`
	DownloadURL string `json:"downloadUrl"`
	SHA256      string `json:"sha256"`
	SizeBytes   int64  `json:"sizeBytes"`
}

// Check calls /api/updates/check and returns non-nil UpdateInfo when a newer
// version is available. Returns nil (no error) when already up to date.
func Check(ctx context.Context, apiURL, currentVersion string) (*UpdateInfo, error) {
	base := strings.TrimRight(apiURL, "/")
	u, err := url.Parse(base + "/api/updates/check")
	if err != nil {
		return nil, fmt.Errorf("building check URL: %w", err)
	}
	q := u.Query()
	q.Set("os", runtime.GOOS)
	q.Set("arch", runtime.GOARCH)
	q.Set("version", currentVersion)
	u.RawQuery = q.Encode()

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, u.String(), nil)
	if err != nil {
		return nil, fmt.Errorf("creating request: %w", err)
	}

	client := &http.Client{Timeout: 15 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("calling update check: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("update check returned %d", resp.StatusCode)
	}

	var cr checkResponse
	if err := json.NewDecoder(resp.Body).Decode(&cr); err != nil {
		return nil, fmt.Errorf("decoding response: %w", err)
	}

	if cr.UpToDate {
		return nil, nil
	}
	return &UpdateInfo{
		Version:     cr.Version,
		DownloadURL: cr.DownloadURL,
		SHA256:      cr.SHA256,
		SizeBytes:   cr.SizeBytes,
	}, nil
}

// Report posts an update outcome (success/failed) back to the backend.
// Best-effort: errors are logged by the caller, not fatal.
func Report(apiURL, endpointID, version, status, reason string) error {
	base := strings.TrimRight(apiURL, "/")
	payload := fmt.Sprintf(
		`{"endpointId":%q,"version":%q,"status":%q,"reason":%q}`,
		endpointID, version, status, reason,
	)
	resp, err := http.Post(
		base+"/api/updates/report",
		"application/json",
		strings.NewReader(payload),
	)
	if err != nil {
		return fmt.Errorf("posting update report: %w", err)
	}
	resp.Body.Close()
	return nil
}
