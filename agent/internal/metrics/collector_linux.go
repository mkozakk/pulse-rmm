//go:build !windows

package metrics

import "github.com/shirou/gopsutil/v3/disk"

func diskUsage() (float64, error) {
	stat, err := disk.Usage("/")
	if err != nil {
		return 0, err
	}
	return stat.UsedPercent, nil
}
