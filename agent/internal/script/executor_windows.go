//go:build windows

package script

import (
	"encoding/base64"
	"os"
	"os/exec"
	"unicode/utf16"
)

func Execute(scriptBody string, envVars map[string]string) (int32, string, error) {
	utf16Encoded := utf16.Encode([]rune(scriptBody))
	b := make([]byte, len(utf16Encoded)*2)
	for i, r := range utf16Encoded {
		b[i*2] = byte(r)
		b[i*2+1] = byte(r >> 8)
	}
	encoded := base64.StdEncoding.EncodeToString(b)

	cmd := exec.Command("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded)
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
