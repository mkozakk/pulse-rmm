//go:build linux
// +build linux

package software

import (
	"bufio"
	"os/exec"
	"strings"
)

func scan() ([]SoftwareItem, error) {
	var allItems []SoftwareItem

	// 1. Try APT (Debian/Ubuntu)
	if items, err := scanAptLinux(); err == nil {
		allItems = append(allItems, items...)
	}

	// 2. Try DNF (Fedora/RHEL)
	// Only run if APT didn't find anything, since systems rarely mix them usefully for base OS
	if len(allItems) == 0 {
		if items, err := scanDnfLinux(); err == nil {
			allItems = append(allItems, items...)
		}
	}

	// 3. Try Flatpak (Desktop Apps - can coexist with APT/DNF)
	if items, err := scanFlatpakLinux(); err == nil {
		allItems = append(allItems, items...)
	}

	return allItems, nil
}

func scanAptLinux() ([]SoftwareItem, error) {
	// dpkg-query is much faster than apt list and has a stable CLI format
	cmd := exec.Command("dpkg-query", "-W", "-f=${binary:Package}|${Version}|${Section}\n")
	output, err := cmd.Output()
	if err != nil {
		return nil, err
	}

	var items []SoftwareItem
	scanner := bufio.NewScanner(strings.NewReader(string(output)))
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		parts := strings.Split(line, "|")
		if len(parts) != 3 {
			continue
		}

		name := parts[0]
		version := parts[1]
		section := parts[2]

		// Filter out noisy system libraries and localized packages
		if strings.HasPrefix(name, "lib") || 
		   strings.HasPrefix(section, "libs") || 
		   strings.HasPrefix(section, "localization") ||
		   strings.HasPrefix(name, "language-pack") {
			continue
		}

		items = append(items, SoftwareItem{
			Name:    name,
			Version: version,
			Source:  "apt",
			ID:      name,
		})
	}

	return items, nil
}

func scanDnfLinux() ([]SoftwareItem, error) {
	cmd := exec.Command("dnf", "list", "installed")
	output, err := cmd.Output()
	if err != nil {
		return nil, err
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

		name := parts[0]
		version := parts[1]

		// Basic filter for DNF too
		if strings.HasPrefix(name, "lib") || strings.Contains(name, "-devel") {
			continue
		}

		items = append(items, SoftwareItem{
			Name:    name,
			Version: version,
			Source:  "dnf",
			ID:      name,
		})
	}

	return items, nil
}

func scanFlatpakLinux() ([]SoftwareItem, error) {
	// Only list apps (no runtimes) to keep it relevant to the user
	cmd := exec.Command("flatpak", "list", "--app", "--columns=application,name,version")
	output, err := cmd.Output()
	if err != nil {
		return nil, err
	}

	var items []SoftwareItem
	scanner := bufio.NewScanner(strings.NewReader(string(output)))
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" {
			continue
		}

		parts := strings.Split(line, "\t")
		if len(parts) >= 3 {
			items = append(items, SoftwareItem{
				Name:    strings.TrimSpace(parts[1]),
				Version: strings.TrimSpace(parts[2]),
				Source:  "flatpak",
				ID:      strings.TrimSpace(parts[0]), // App ID like org.mozilla.firefox
			})
		}
	}

	return items, nil
}
