package update

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"net/http"
	"os"
	"time"
)

// Download fetches url into destPath while computing its sha256. Returns an
// error if the download fails or the hash does not match expectedSHA256.
func Download(ctx context.Context, rawURL, destPath, expectedSHA256 string) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, rawURL, nil)
	if err != nil {
		return fmt.Errorf("creating download request: %w", err)
	}

	client := &http.Client{Timeout: 10 * time.Minute}
	resp, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("downloading binary: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("download returned %d", resp.StatusCode)
	}

	f, err := os.OpenFile(destPath, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0755)
	if err != nil {
		return fmt.Errorf("creating destination file: %w", err)
	}
	defer f.Close()

	h := sha256.New()
	if _, err := io.Copy(io.MultiWriter(f, h), resp.Body); err != nil {
		os.Remove(destPath)
		return fmt.Errorf("writing download: %w", err)
	}

	got := hex.EncodeToString(h.Sum(nil))
	if got != expectedSHA256 {
		os.Remove(destPath)
		return fmt.Errorf("sha256 mismatch: expected %s got %s", expectedSHA256, got)
	}
	return nil
}
