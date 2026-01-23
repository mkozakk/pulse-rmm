package main

import (
	"context"
	"fmt"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/pulsermm/pulse-rmm/agent/internal/control"
	"github.com/pulsermm/pulse-rmm/agent/internal/enrolment"
	"github.com/pulsermm/pulse-rmm/agent/internal/metrics"
	"github.com/pulsermm/pulse-rmm/agent/internal/store"
	pb "github.com/pulsermm/pulse-rmm/agent/gen/pulse/v1"
)

func main() {
	token := os.Getenv("PULSE_TOKEN")
	if token == "" {
		fmt.Fprintf(os.Stderr, "Usage: PULSE_TOKEN=<token> [PULSE_SERVER=host:port] [PULSE_METRIC_SERVER=host:port] %s\n", os.Args[0])
		os.Exit(1)
	}

	grpcAddr := os.Getenv("PULSE_SERVER")
	if grpcAddr == "" {
		grpcAddr = "localhost:9091"
	}

	metricAddr := os.Getenv("PULSE_METRIC_SERVER")
	if metricAddr == "" {
		metricAddr = "localhost:9092"
	}

	gatewayAddr := os.Getenv("PULSE_GATEWAY")
	if gatewayAddr == "" {
		gatewayAddr = "localhost:9090"
	}

	privKey, err := store.LoadOrGenerateKey()
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		os.Exit(1)
	}

	endpointID, err := store.LoadEndpointID()
	if err != nil {
		endpointID, err = enrolment.Enrol(context.Background(), token, grpcAddr, privKey)
		if err != nil {
			fmt.Fprintf(os.Stderr, "Error: %v\n", err)
			os.Exit(1)
		}
		if err := store.SaveEndpointID(endpointID); err != nil {
			fmt.Fprintf(os.Stderr, "Error: %v\n", err)
			os.Exit(1)
		}
		fmt.Printf("Enrolled: %s\n", endpointID)
	} else {
		fmt.Printf("Already enrolled: %s\n", endpointID)
	}

	metricClient, err := metrics.NewClient(grpcAddr, metricAddr)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		os.Exit(1)
	}
	defer metricClient.Close()

	ctx, cancel := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer cancel()

	go runHeartbeat(ctx, metricClient, endpointID)
	go runMetrics(ctx, metricClient, endpointID)
	go runControlStream(ctx, endpointID, gatewayAddr)

	<-ctx.Done()
	fmt.Println("Shutting down")
}

func runControlStream(ctx context.Context, endpointID, gatewayAddr string) {
	inCh := make(chan *pb.GatewayCommand, 16)
	outCh := make(chan *pb.AgentEvent, 16)

	// drain inCh — Phase 4 wires in the shell dispatcher
	go func() {
		for range inCh {
		}
	}()

	backoff := 250 * time.Millisecond
	for ctx.Err() == nil {
		err := control.Run(ctx, endpointID, gatewayAddr, "0.5.0", inCh, outCh)
		if ctx.Err() != nil {
			return
		}
		fmt.Fprintf(os.Stderr, "control stream ended: %v; retrying in %s\n", err, backoff)
		select {
		case <-time.After(backoff):
		case <-ctx.Done():
			return
		}
		if backoff < 30*time.Second {
			backoff *= 2
		}
	}
}

func runHeartbeat(ctx context.Context, client *metrics.Client, endpointID string) {
	ticker := time.NewTicker(30 * time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			if err := client.Heartbeat(ctx, endpointID); err != nil {
				fmt.Fprintf(os.Stderr, "heartbeat error: %v\n", err)
			}
		}
	}
}

func runMetrics(ctx context.Context, client *metrics.Client, endpointID string) {
	ticker := time.NewTicker(30 * time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			samples, err := metrics.Collect()
			if err != nil {
				fmt.Fprintf(os.Stderr, "metrics collect error: %v\n", err)
				continue
			}
			if err := client.PushMetrics(ctx, endpointID, samples); err != nil {
				fmt.Fprintf(os.Stderr, "metrics push error: %v\n", err)
			}
		}
	}
}
