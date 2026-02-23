package software

import (
	"fmt"
	"os/exec"
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

