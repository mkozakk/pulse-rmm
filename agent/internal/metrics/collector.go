package metrics

import (
	"fmt"
	"strconv"
	"time"

	"github.com/shirou/gopsutil/v3/cpu"
	"github.com/shirou/gopsutil/v3/disk"
	"github.com/shirou/gopsutil/v3/mem"
	"github.com/shirou/gopsutil/v3/net"
)

type Sample struct {
	Type        string
	Value       float64
	CollectedAt time.Time
	Labels      map[string]string
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

	perCore, err := cpu.Percent(0, true)
	if err == nil {
		for i, v := range perCore {
			samples = append(samples, Sample{
				Type:        "cpu.core",
				Value:       v,
				CollectedAt: now,
				Labels:      map[string]string{"core": strconv.Itoa(i)},
			})
		}
	}

	vmStat, err := mem.VirtualMemory()
	if err != nil {
		return nil, fmt.Errorf("collecting ram: %w", err)
	}
	samples = append(samples,
		Sample{Type: "ram", Value: vmStat.UsedPercent, CollectedAt: now},
		Sample{Type: "ram.used_bytes", Value: float64(vmStat.Used), CollectedAt: now},
		Sample{Type: "ram.available_bytes", Value: float64(vmStat.Available), CollectedAt: now},
		Sample{Type: "ram.total_bytes", Value: float64(vmStat.Total), CollectedAt: now},
	)

	if sm, err := mem.SwapMemory(); err == nil {
		samples = append(samples,
			Sample{Type: "swap.used_bytes", Value: float64(sm.Used), CollectedAt: now},
			Sample{Type: "swap.total_bytes", Value: float64(sm.Total), CollectedAt: now},
		)
	}

	if diskPct, err := diskUsage(); err == nil {
		samples = append(samples, Sample{Type: "disk", Value: diskPct, CollectedAt: now})
	}

	parts, err := disk.Partitions(false)
	if err == nil {
		for _, p := range parts {
			usage, err := disk.Usage(p.Mountpoint)
			if err != nil || usage.Total == 0 {
				continue
			}
			labels := map[string]string{"device": p.Device, "mount": p.Mountpoint}
			samples = append(samples,
				Sample{Type: "disk.used_bytes", Value: float64(usage.Used), CollectedAt: now, Labels: labels},
				Sample{Type: "disk.free_bytes", Value: float64(usage.Free), CollectedAt: now, Labels: labels},
				Sample{Type: "disk.total_bytes", Value: float64(usage.Total), CollectedAt: now, Labels: labels},
			)
		}
	}

	nicStats, err := net.IOCounters(true)
	if err == nil {
		for _, n := range nicStats {
			labels := map[string]string{"nic": n.Name}
			samples = append(samples,
				Sample{Type: "net.rx_bytes", Value: float64(n.BytesRecv), CollectedAt: now, Labels: labels},
				Sample{Type: "net.tx_bytes", Value: float64(n.BytesSent), CollectedAt: now, Labels: labels},
				Sample{Type: "net.rx_packets", Value: float64(n.PacketsRecv), CollectedAt: now, Labels: labels},
				Sample{Type: "net.tx_packets", Value: float64(n.PacketsSent), CollectedAt: now, Labels: labels},
			)
		}
	}

	return samples, nil
}
