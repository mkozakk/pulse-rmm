package software

import (
	"fmt"
	"os/exec"
	"os/user"
)

func Execute(action, name, version, id string) (int32, string, error) {
	switch action {
	case "install":
		return -1, "", fmt.Errorf("install action is not supported (requires manual package IDs)")
	case "update":
		return -1, "", fmt.Errorf("update action is not supported (requires manual package IDs)")
	case "remove":
		return osExecuteRemove(name, id)
	default:
		return -1, "", fmt.Errorf("unknown action: %s", action)
	}
}

func executeWingetCommand(action, name, version, id string) (int32, string, error) {
	var args []string
	args = append(args, action)
	
	// winget prefers ID, fallback to Name
	if id != "" {
		args = append(args, "--exact", "--id", id)
	} else {
		args = append(args, "--exact", "--name", name)
	}
	
	args = append(args, "--silent", "--accept-source-agreements")
	
	if version != "" && action == "install" {
		args = append(args, "--version", version)
	}

	cmd := exec.Command("winget", args...)
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

func executeAptCommand(args ...string) (int32, string, error) {
	// Check if running as root
	currentUser, err := user.Current()
	isRoot := err == nil && currentUser.Uid == "0"

	var cmd *exec.Cmd
	var cmdStr string
	if isRoot {
		// Running as root, don't use sudo
		cmd = exec.Command("apt-get", args...)
		cmdStr = fmt.Sprintf("apt-get %v", args)
	} else {
		// Not root, use sudo
		fullArgs := append([]string{"apt-get"}, args...)
		cmd = exec.Command("sudo", fullArgs...)
		cmdStr = fmt.Sprintf("sudo apt-get %v", args)
	}

	fmt.Printf("[executor] Running: %s\n", cmdStr)
	fmt.Printf("[executor] IsRoot: %v\n", isRoot)
	fmt.Printf("[executor] WorkDir: %s\n", cmd.Dir)
	fmt.Printf("[executor] Env count: %d\n", len(cmd.Env))

	output, err := cmd.CombinedOutput()

	exitCode := int32(0)
	if err != nil {
		if exitErr, ok := err.(*exec.ExitError); ok {
			exitCode = int32(exitErr.ExitCode())
		} else {
			exitCode = -1
		}
	}

	fmt.Printf("[executor] Exit code: %d\n", exitCode)
	fmt.Printf("[executor] Output length: %d bytes\n", len(output))
	fmt.Printf("[executor] Output:\n%s\n", string(output))

	return exitCode, string(output), nil
}
