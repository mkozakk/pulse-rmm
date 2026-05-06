//go:build linux || darwin

package script

import (
	"fmt"
	"os"
	"os/exec"
)

func Execute(scriptBody string, envVars map[string]string) (int32, string, error) {
	tmpFile, err := os.CreateTemp("", "pulse-script-*.sh")
	if err != nil {
		return -1, "", fmt.Errorf("creating temp file: %w", err)
	}
	defer os.Remove(tmpFile.Name())

	if _, err := tmpFile.WriteString(scriptBody); err != nil {
		tmpFile.Close()
		return -1, "", fmt.Errorf("writing script: %w", err)
	}
	tmpFile.Close()

	if err := os.Chmod(tmpFile.Name(), 0700); err != nil {
		return -1, "", fmt.Errorf("chmod script: %w", err)
	}

	cmd := exec.Command("bash", tmpFile.Name())
	if envVars != nil {
		cmd.Env = append(os.Environ())
		for k, v := range envVars {
			cmd.Env = append(cmd.Env, k+"="+v)
		}
	}

	output, err := cmd.CombinedOutput()
	outputStr := string(output)

	if err == nil {
		return 0, outputStr, nil
	}

	exitCode := int32(-1)
	if exitErr, ok := err.(*exec.ExitError); ok {
		exitCode = int32(exitErr.ExitCode())
	}

	return exitCode, outputStr, nil
}
