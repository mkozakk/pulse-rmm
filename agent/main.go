package main

import (
	"context"
	"fmt"
	"os"

	"github.com/pulsermm/pulse-rmm/agent/internal/enrolment"
	"github.com/pulsermm/pulse-rmm/agent/internal/store"
)

func main() {
	token := os.Getenv("PULSE_TOKEN")
	if token == "" {
		fmt.Fprintf(os.Stderr, "Usage: PULSE_TOKEN=<token> [PULSE_SERVER=host:port] %s\n", os.Args[0])
		os.Exit(1)
	}

	grpcAddr := os.Getenv("PULSE_SERVER")
	if grpcAddr == "" {
		grpcAddr = "localhost:9091"
	}

	privKey, err := store.LoadOrGenerateKey()
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		os.Exit(1)
	}

	endpointID, err := store.LoadEndpointID()
	if err == nil {
		fmt.Printf("Already enrolled: %s\n", endpointID)
		return
	}

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
}
