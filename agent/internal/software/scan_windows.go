//go:build windows
// +build windows

package software

import (
	"bufio"
	"fmt"
	"os/exec"
	"strings"
)

func scan() ([]SoftwareItem, error) {
	cmd := exec.Command("choco", "list", "--local-only", "-r")
	output, err := cmd.Output()
	if err != nil {
		return nil, fmt.Errorf("choco list failed: %w", err)
	}

	var items []SoftwareItem
	scanner := bufio.NewScanner(strings.NewReader(string(output)))
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" {
			continue
		}

		parts := strings.Split(line, "|")
		if len(parts) < 2 {
			continue
		}

		items = append(items, SoftwareItem{
			Name:    strings.TrimSpace(parts[0]),
			Version: strings.TrimSpace(parts[1]),
			Source:  "chocolatey",
		})
	}

	if err := scanner.Err(); err != nil {
		return nil, fmt.Errorf("scan error: %w", err)
	}

	return items, nil
}
