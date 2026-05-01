//go:build linux
// +build linux

package software

import (
	"bufio"
	"fmt"
	"os/exec"
	"strings"
)

func scan() ([]SoftwareItem, error) {
	// Try apt first (Debian/Ubuntu)
	items, err := scanAptLinux()
	if err == nil {
		return items, nil
	}

	// Fallback to dnf (Fedora/RHEL)
	return scanDnfLinux()
}

func scanAptLinux() ([]SoftwareItem, error) {
	cmd := exec.Command("apt", "list", "--installed")
	output, err := cmd.Output()
	if err != nil {
		return nil, fmt.Errorf("apt list failed: %w", err)
	}

	var items []SoftwareItem
	scanner := bufio.NewScanner(strings.NewReader(string(output)))
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "Listing") {
			continue
		}

		parts := strings.Split(line, "/")
		if len(parts) < 1 {
			continue
		}

		pkgName := strings.TrimSpace(parts[0])
		version := "unknown"

		// Extract version from the line (after package name)
		if idx := strings.Index(line, " "); idx > 0 {
			versionPart := strings.TrimSpace(line[idx:])
			versionParts := strings.Fields(versionPart)
			if len(versionParts) > 0 {
				version = strings.TrimSuffix(versionParts[0], ",")
			}
		}

		items = append(items, SoftwareItem{
			Name:    pkgName,
			Version: version,
			Source:  "apt",
		})
	}

	if err := scanner.Err(); err != nil {
		return nil, fmt.Errorf("scan error: %w", err)
	}

	return items, nil
}

func scanDnfLinux() ([]SoftwareItem, error) {
	cmd := exec.Command("dnf", "list", "installed")
	output, err := cmd.Output()
	if err != nil {
		return nil, fmt.Errorf("dnf list failed: %w", err)
	}

	var items []SoftwareItem
	scanner := bufio.NewScanner(strings.NewReader(string(output)))
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.Contains(line, "Installed Packages") {
			continue
		}

		parts := strings.Fields(line)
		if len(parts) < 2 {
			continue
		}

		items = append(items, SoftwareItem{
			Name:    parts[0],
			Version: parts[1],
			Source:  "dnf",
		})
	}

	if err := scanner.Err(); err != nil {
		return nil, fmt.Errorf("scan error: %w", err)
	}

	return items, nil
}
