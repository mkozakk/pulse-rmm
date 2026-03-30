package sysinfo

import (
	"fmt"
	"net"
	"time"

	"github.com/shirou/gopsutil/v3/cpu"
	"github.com/shirou/gopsutil/v3/disk"
	"github.com/shirou/gopsutil/v3/mem"
)

type CPU struct {
	Model         string
	PhysicalCores int32
	LogicalCores  int32
	FrequencyMHz  float64
}

type Memory struct {
	TotalBytes     uint64
	SwapTotalBytes uint64
}

type Disk struct {
	Device     string
	Mountpoint string
	Fstype     string
	TotalBytes uint64
}

type Nic struct {
	Name      string
	MAC       string
	Addresses []string
	MTU       uint32
}

type Info struct {
	CPU         CPU
	Memory      Memory
	Disks       []Disk
	Nics        []Nic
	CollectedAt time.Time
}

func Collect() (Info, error) {
	info := Info{CollectedAt: time.Now()}

	cpuInfos, err := cpu.Info()
	if err != nil {
		return info, fmt.Errorf("cpu info: %w", err)
	}
	logical, err := cpu.Counts(true)
	if err != nil {
		return info, fmt.Errorf("cpu logical count: %w", err)
	}
	physical, err := cpu.Counts(false)
	if err != nil {
		physical = logical
	}
	info.CPU.LogicalCores = int32(logical)
	info.CPU.PhysicalCores = int32(physical)
	if len(cpuInfos) > 0 {
		info.CPU.Model = cpuInfos[0].ModelName
		info.CPU.FrequencyMHz = cpuInfos[0].Mhz
	}

	vm, err := mem.VirtualMemory()
	if err != nil {
		return info, fmt.Errorf("memory: %w", err)
	}
	info.Memory.TotalBytes = vm.Total

	if sm, err := mem.SwapMemory(); err == nil {
		info.Memory.SwapTotalBytes = sm.Total
	}

	parts, err := disk.Partitions(false)
	if err != nil {
		return info, fmt.Errorf("disk partitions: %w", err)
	}
	for _, p := range parts {
		usage, err := disk.Usage(p.Mountpoint)
		if err != nil || usage.Total == 0 {
			continue
		}
		info.Disks = append(info.Disks, Disk{
			Device:     p.Device,
			Mountpoint: p.Mountpoint,
			Fstype:     p.Fstype,
			TotalBytes: usage.Total,
		})
	}

	ifaces, err := net.Interfaces()
	if err != nil {
		return info, fmt.Errorf("net interfaces: %w", err)
	}
	for _, ifc := range ifaces {
		nic := Nic{
			Name: ifc.Name,
			MAC:  ifc.HardwareAddr.String(),
			MTU:  uint32(ifc.MTU),
		}
		addrs, err := ifc.Addrs()
		if err == nil {
			for _, a := range addrs {
				nic.Addresses = append(nic.Addresses, a.String())
			}
		}
		info.Nics = append(info.Nics, nic)
	}

	return info, nil
}
