//go:build windows
// +build windows

package software

import (
	"bufio"
	"fmt"
	"os/exec"
	"strings"
)

func ParseWingetList(output string) []SoftwareItem {
	var apps []SoftwareItem
	scanner := bufio.NewScanner(strings.NewReader(output))

	var header string
	for scanner.Scan() {
		line := scanner.Text()
		if strings.HasPrefix(line, "Name") && strings.Contains(line, "Id") && strings.Contains(line, "Version") {
			header = line
			break
		}
	}

	if header == "" {
		return apps
	}

	scanner.Scan() // skip the dashes line

	nameIdx := strings.Index(header, "Name")
	idIdx := strings.Index(header, "Id")
	versionIdx := strings.Index(header, "Version")
	availableIdx := strings.Index(header, "Available")
	sourceIdx := strings.Index(header, "Source")

	if nameIdx == -1 || idIdx == -1 || versionIdx == -1 {
		return apps
	}

	blacklist := []string{
		"VCRedist", "DirectX", "Update Health Tools",
		"Windows Driver Package", "Microsoft .NET",
		"WindowsAppRuntime", "Language Experience Pack",
	}

	getCol := func(line string, start, end int) string {
		if start >= len(line) || start == -1 {
			return ""
		}
		if end == -1 || end > len(line) {
			return strings.TrimSpace(line[start:])
		}
		return strings.TrimSpace(line[start:end])
	}

	for scanner.Scan() {
		line := scanner.Text()
		if strings.TrimSpace(line) == "" {
			continue
		}

		name := getCol(line, nameIdx, idIdx)
		id := getCol(line, idIdx, versionIdx)
		
		var version, available, source string
		if availableIdx != -1 && sourceIdx != -1 {
			version = getCol(line, versionIdx, availableIdx)
			available = getCol(line, availableIdx, sourceIdx)
			source = getCol(line, sourceIdx, -1)
		} else if sourceIdx != -1 {
			version = getCol(line, versionIdx, sourceIdx)
			source = getCol(line, sourceIdx, -1)
		} else if availableIdx != -1 {
			version = getCol(line, versionIdx, availableIdx)
			available = getCol(line, availableIdx, -1)
		} else {
			version = getCol(line, versionIdx, -1)
		}

		available = strings.ReplaceAll(available, "<", "")
		available = strings.ReplaceAll(available, ">", "")
		available = strings.TrimSpace(available)

		blacklisted := false
		for _, b := range blacklist {
			if strings.Contains(name, b) || strings.Contains(id, b) {
				blacklisted = true
				break
			}
		}
		if blacklisted {
			continue
		}

		hasSource := source != ""
		isRegistryPath := strings.HasPrefix(id, "ARP\\") || strings.HasPrefix(id, "MSIX\\")
		hasAvailable := available != ""

		if hasSource || !isRegistryPath || hasAvailable {
			apps = append(apps, SoftwareItem{
				Name:     name,
				ID:       id,
				Version:  version,
				Source:   source,
				UpdateTo: available,
				IsStore:  source == "msstore",
			})
		}
	}

	return apps
}

func scan() ([]SoftwareItem, error) {
	cmd := exec.Command("winget", "list", "--accept-source-agreements")
	output, err := cmd.Output()
	if err != nil {
		return nil, fmt.Errorf("winget list failed: %w", err)
	}

	return ParseWingetList(string(output)), nil
}
