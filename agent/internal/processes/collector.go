package processes

import (
	"fmt"

	"github.com/shirou/gopsutil/v3/process"
)

type Info struct {
	PID         int32   `json:"pid"`
	Name        string  `json:"name"`
	Username    string  `json:"username"`
	CPUPercent  float64 `json:"cpuPercent"`
	MemoryBytes uint64  `json:"memoryBytes"`
}

func List() ([]Info, error) {
	procs, err := process.Processes()
	if err != nil {
		return nil, fmt.Errorf("listing processes: %w", err)
	}
	out := make([]Info, 0, len(procs))
	for _, p := range procs {
		name, _ := p.Name()
		user, _ := p.Username()
		cpu, _ := p.CPUPercent()
		var rss uint64
		if mem, err := p.MemoryInfo(); err == nil && mem != nil {
			rss = mem.RSS
		}
		out = append(out, Info{
			PID:         p.Pid,
			Name:        name,
			Username:    user,
			CPUPercent:  cpu,
			MemoryBytes: rss,
		})
	}
	return out, nil
}

func Kill(pid int32) error {
	p, err := process.NewProcess(pid)
	if err != nil {
		return fmt.Errorf("finding pid %d: %w", pid, err)
	}
	if err := p.Kill(); err != nil {
		return fmt.Errorf("killing pid %d: %w", pid, err)
	}
	return nil
}
