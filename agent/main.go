package main

import (
	"context"
	"fmt"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/pulsermm/pulse-rmm/agent/internal/enrolment"
	"github.com/pulsermm/pulse-rmm/agent/internal/metrics"
	"github.com/pulsermm/pulse-rmm/agent/internal/store"
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

	<-ctx.Done()
	fmt.Println("Shutting down")
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
