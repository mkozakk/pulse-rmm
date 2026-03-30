package metrics

import (
	"context"
	"fmt"

	pb "github.com/pulsermm/pulse-rmm/agent/gen/pulse/v1"
	"github.com/pulsermm/pulse-rmm/agent/internal/sysinfo"

	"google.golang.org/grpc"
)

type Client struct {
	agentConn    *grpc.ClientConn
	metricConn   *grpc.ClientConn
	agentClient  pb.AgentServiceClient
	metricClient pb.MetricServiceClient
}

func NewClient(agentAddr, metricAddr string, opts ...grpc.DialOption) (*Client, error) {
	agentConn, err := grpc.NewClient(agentAddr, opts...)
	if err != nil {
		return nil, fmt.Errorf("dialing agent server: %w", err)
	}
	metricConn, err := grpc.NewClient(metricAddr, opts...)
	if err != nil {
		agentConn.Close()
		return nil, fmt.Errorf("dialing metric server: %w", err)
	}
	return &Client{
		agentConn:    agentConn,
		metricConn:   metricConn,
		agentClient:  pb.NewAgentServiceClient(agentConn),
		metricClient: pb.NewMetricServiceClient(metricConn),
	}, nil
}

func (c *Client) Close() {
	c.agentConn.Close()
	c.metricConn.Close()
}

func (c *Client) Heartbeat(ctx context.Context, endpointID string) error {
	_, err := c.agentClient.Heartbeat(ctx, &pb.HeartbeatRequest{EndpointId: endpointID})
	if err != nil {
		return fmt.Errorf("heartbeat rpc: %w", err)
	}
	return nil
}

func (c *Client) PushMetrics(ctx context.Context, endpointID string, samples []Sample) error {
	var pbSamples []*pb.MetricSample
	for _, s := range samples {
		pbSamples = append(pbSamples, &pb.MetricSample{
			Type:        s.Type,
			Value:       s.Value,
			CollectedAt: s.CollectedAt.UnixMilli(),
			Labels:      s.Labels,
		})
	}

	_, err := c.metricClient.PushMetrics(ctx, &pb.MetricBatch{
		EndpointId: endpointID,
		Samples:    pbSamples,
	})
	if err != nil {
		return fmt.Errorf("push metrics rpc: %w", err)
	}
	return nil
}

func (c *Client) ReportSystemInfo(ctx context.Context, endpointID string, info sysinfo.Info) error {
	disks := make([]*pb.DiskInfo, 0, len(info.Disks))
	for _, d := range info.Disks {
		disks = append(disks, &pb.DiskInfo{
			Device:     d.Device,
			Mountpoint: d.Mountpoint,
			Fstype:     d.Fstype,
			TotalBytes: d.TotalBytes,
		})
	}
	nics := make([]*pb.NicInfo, 0, len(info.Nics))
	for _, n := range info.Nics {
		nics = append(nics, &pb.NicInfo{
			Name:      n.Name,
			Mac:       n.MAC,
			Addresses: n.Addresses,
			Mtu:       n.MTU,
		})
	}

	_, err := c.metricClient.ReportSystemInfo(ctx, &pb.SystemInfo{
		EndpointId: endpointID,
		Cpu: &pb.CpuInfo{
			Model:         info.CPU.Model,
			PhysicalCores: info.CPU.PhysicalCores,
			LogicalCores:  info.CPU.LogicalCores,
			FrequencyMhz:  info.CPU.FrequencyMHz,
		},
		Memory: &pb.MemoryInfo{
			TotalBytes:     info.Memory.TotalBytes,
			SwapTotalBytes: info.Memory.SwapTotalBytes,
		},
		Disks:       disks,
		Nics:        nics,
		CollectedAt: info.CollectedAt.UnixMilli(),
	})
	if err != nil {
		return fmt.Errorf("report system info rpc: %w", err)
	}
	return nil
}
