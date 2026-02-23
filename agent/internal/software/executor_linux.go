//go:build linux
// +build linux

package software

import (
	"fmt"
	"os/exec"
	"os/user"
	"strings"
)

func osExecuteRemove(name, id string) (int32, string, error) {
	// Fallback to name if id is empty
	target := id
	if target == "" {
		target = name
	}

	// If the target looks like a Flatpak app ID (e.g. org.mozilla.firefox)
	if strings.Contains(target, ".") && !strings.Contains(target, " ") {
		// Verify if flatpak is installed
		if _, err := exec.LookPath("flatpak"); err == nil {
			return executeLinuxCommand("flatpak", "uninstall", "-y", target)
		}
	}

	// Try DNF if installed
	if _, err := exec.LookPath("dnf"); err == nil {
		return executeLinuxCommand("dnf", "remove", "-y", target)
	}

	// Default to APT
	return executeLinuxCommand("apt-get", "remove", "-y", target)
}

func executeLinuxCommand(binary string, args ...string) (int32, string, error) {
	currentUser, err := user.Current()
	isRoot := err == nil && currentUser.Uid == "0"

	var cmd *exec.Cmd
	// Flatpak can run as user or system, but generally we just run it. 
	// apt-get and dnf need root.
	if isRoot || binary == "flatpak" {
		cmd = exec.Command(binary, args...)
	} else {
		fullArgs := append([]string{binary}, args...)
		cmd = exec.Command("sudo", fullArgs...)
	}

	cmdStr := strings.Join(cmd.Args, " ")
	fmt.Printf("[executor] Running: %s\n", cmdStr)

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
