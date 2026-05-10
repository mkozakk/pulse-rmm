package metrics

import (
	"fmt"
	"time"

	"github.com/shirou/gopsutil/v3/cpu"
	"github.com/shirou/gopsutil/v3/mem"
)

type Sample struct {
	Type        string
	Value       float64
	CollectedAt time.Time
}

func Collect() ([]Sample, error) {
	now := time.Now()
	var samples []Sample

	cpuPct, err := cpu.Percent(0, false)
	if err != nil {
		return nil, fmt.Errorf("collecting cpu: %w", err)
	}
	if len(cpuPct) > 0 {
		samples = append(samples, Sample{Type: "cpu", Value: cpuPct[0], CollectedAt: now})
	}

	vmStat, err := mem.VirtualMemory()
	if err != nil {
		return nil, fmt.Errorf("collecting ram: %w", err)
	}
	samples = append(samples, Sample{Type: "ram", Value: vmStat.UsedPercent, CollectedAt: now})

	if diskPct, err := diskUsage(); err == nil {
		samples = append(samples, Sample{Type: "disk", Value: diskPct, CollectedAt: now})
	}

	return samples, nil
}
