package software

import (
	"fmt"
	"os/exec"
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
	cmd := exec.Command("apt-get", args...)
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
