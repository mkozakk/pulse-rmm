package metrics

import (
	"context"
	"fmt"

	pb "github.com/pulsermm/pulse-rmm/agent/gen/pulse/v1"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
)

type Client struct {
	agentConn    *grpc.ClientConn
	metricConn   *grpc.ClientConn
	agentClient  pb.AgentServiceClient
	metricClient pb.MetricServiceClient
}

func NewClient(agentAddr, metricAddr string) (*Client, error) {
	agentConn, err := grpc.NewClient(agentAddr, grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		return nil, fmt.Errorf("dialing agent server: %w", err)
	}
	metricConn, err := grpc.NewClient(metricAddr, grpc.WithTransportCredentials(insecure.NewCredentials()))
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
