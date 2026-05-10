//go:build windows

package metrics

import (
	"os"
	"path/filepath"

	"github.com/shirou/gopsutil/v3/disk"
)

func diskUsage() (float64, error) {
	exe, err := os.Executable()
	if err != nil {
		exe = "C:\\"
	}
	vol := filepath.VolumeName(exe)
	if vol == "" {
		vol = "C:"
	}
	stat, err := disk.Usage(vol + "\\")
	if err != nil {
		return 0, err
	}
	return stat.UsedPercent, nil
}
