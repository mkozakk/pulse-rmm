package software

import (
	"fmt"
	"os/exec"
	"os/user"
	"runtime"
)

func Execute(action, name, version string) (int32, string, error) {
	switch action {
	case "install":
		return executeInstall(name, version)
	case "update":
		return executeUpdate(name, version)
	case "remove":
		return executeRemove(name)
	default:
		return -1, "", fmt.Errorf("unknown action: %s", action)
	}
}

func executeInstall(name, version string) (int32, string, error) {
	if runtime.GOOS == "windows" {
		return executeChocoCommand("install", name, version)
	}
	return executeAptCommand("install", name, version)
}

func executeUpdate(name, version string) (int32, string, error) {
	if runtime.GOOS == "windows" {
		return executeChocoCommand("upgrade", name, version)
	}
	return executeAptCommand("install", "--upgrade", name)
}

func executeRemove(name string) (int32, string, error) {
	if runtime.GOOS == "windows" {
		return executeChocoCommand("uninstall", name, "")
	}
	return executeAptCommand("remove", name)
}

func executeChocoCommand(action, name, version string) (int32, string, error) {
	var args []string
	args = append(args, action, "-y", name)
	if version != "" && action == "install" {
		args = append(args, "--version", version)
	}

	cmd := exec.Command("choco", args...)
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
