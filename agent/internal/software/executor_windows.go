//go:build windows
// +build windows

package software

import (
	"os/exec"
	"strings"

	"golang.org/x/sys/windows/registry"
)

func osExecuteRemove(name, id string) (int32, string, error) {
	// Look up the ID in the registry to find the uninstall string
	keys := []struct {
		k    registry.Key
		path string
	}{
		{registry.LOCAL_MACHINE, `SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall`},
		{registry.LOCAL_MACHINE, `SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall`},
		{registry.CURRENT_USER, `SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall`},
		{registry.CURRENT_USER, `SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall`},
	}

	var uninstallCmd string

	for _, kv := range keys {
		k, err := registry.OpenKey(kv.k, kv.path+`\`+id, registry.READ)
		if err == nil {
			// Prefer QuietUninstallString
			quietCmd, _, err := k.GetStringValue("QuietUninstallString")
			if err == nil && quietCmd != "" {
				uninstallCmd = quietCmd
			} else {
				// Fallback to normal UninstallString
				normalCmd, _, err := k.GetStringValue("UninstallString")
				if err == nil && normalCmd != "" {
					uninstallCmd = normalCmd
				}
			}
			k.Close()
			if uninstallCmd != "" {
				break
			}
		}
	}

	if uninstallCmd == "" {
		// Fallback to winget if registry doesn't have it (maybe it's a store app or winget package)
		return executeWingetCommand("uninstall", name, "", id)
	}

	// Clean up the command
	// MSI exec needs special handling for silent uninstall
	lowerCmd := strings.ToLower(uninstallCmd)
	if strings.Contains(lowerCmd, "msiexec") {
		// Replace /I with /X for uninstall if needed
		uninstallCmd = strings.Replace(uninstallCmd, "/I", "/X", 1)
		uninstallCmd = strings.Replace(uninstallCmd, "/i", "/X", 1)
		
		// Add silent flags if not present
		if !strings.Contains(lowerCmd, "/qn") && !strings.Contains(lowerCmd, "/quiet") {
			uninstallCmd += " /qn /norestart"
		}
	}

	cmd := exec.Command("cmd", "/c", uninstallCmd)
	output, err := cmd.CombinedOutput()

	exitCode := int32(0)
	if err != nil {
		if exitErr, ok := err.(*exec.ExitError); ok {
			exitCode = int32(exitErr.ExitCode())
		} else {
			exitCode = -1
		}
	}

	return exitCode, string(output), nil
}
