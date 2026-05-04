package main

import (
	"context"
	"fmt"
	"os"
	"os/signal"
	"syscall"
	"time"

	pb "github.com/pulsermm/pulse-rmm/agent/gen/pulse/v1"
	"github.com/pulsermm/pulse-rmm/agent/internal/control"
	"github.com/pulsermm/pulse-rmm/agent/internal/enrolment"
	"github.com/pulsermm/pulse-rmm/agent/internal/metrics"
	"github.com/pulsermm/pulse-rmm/agent/internal/script"
	"github.com/pulsermm/pulse-rmm/agent/internal/shell"
	"github.com/pulsermm/pulse-rmm/agent/internal/software"
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
	go runSoftwareScan(ctx, endpointID)
	go runControlStream(ctx, endpointID, gatewayAddr)

	<-ctx.Done()
	fmt.Println("Shutting down")
}

func runControlStream(ctx context.Context, endpointID, gatewayAddr string) {
	inCh := make(chan *pb.GatewayCommand, 16)
	outCh := make(chan *pb.AgentEvent, 16)

	shellMgr := shell.NewManager(outCh)
	defer shellMgr.CloseAll()

	go func() {
		for {
			select {
			case cmd, ok := <-inCh:
				if !ok {
					return
				}
				dispatchCmd(shellMgr, cmd, outCh)
			case <-ctx.Done():
				return
			}
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

func dispatchCmd(mgr *shell.Manager, cmd *pb.GatewayCommand, outCh chan<- *pb.AgentEvent) {
	switch p := cmd.Payload.(type) {
	case *pb.GatewayCommand_OpenShell:
		o := p.OpenShell
		if err := mgr.Open(o.SessionId, o.Cols, o.Rows); err != nil {
			outCh <- &pb.AgentEvent{
				Payload: &pb.AgentEvent_ShellExited{
					ShellExited: &pb.ShellExited{SessionId: o.SessionId, ExitCode: -1, Error: err.Error()},
				},
			}
			return
		}
		outCh <- &pb.AgentEvent{
			Payload: &pb.AgentEvent_ShellStarted{
				ShellStarted: &pb.ShellStarted{SessionId: o.SessionId},
			},
		}
	case *pb.GatewayCommand_ShellInput:
		mgr.Input(p.ShellInput.SessionId, p.ShellInput.Data)
	case *pb.GatewayCommand_ShellResize:
		s := p.ShellResize
		mgr.Resize(s.SessionId, s.Cols, s.Rows)
	case *pb.GatewayCommand_CloseShell:
		mgr.Close(p.CloseShell.SessionId)
	case *pb.GatewayCommand_SoftwareCommand:
		go executeSoftwareCommand(p.SoftwareCommand, outCh)
	case *pb.GatewayCommand_ScriptCommand:
		go executeScriptCommand(p.ScriptCommand, outCh)
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

func runSoftwareScan(ctx context.Context, endpointID string) {
	ticker := time.NewTicker(10 * time.Minute)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			items, err := software.Scan()
			if err != nil {
				fmt.Fprintf(os.Stderr, "software scan error: %v\n", err)
				continue
			}
			fmt.Printf("Scanned %d software items\n", len(items))
		}
	}
}

func executeSoftwareCommand(cmd *pb.SoftwareCommand, outCh chan<- *pb.AgentEvent) {
	exitCode, output, err := software.Execute(cmd.Action, cmd.Name, cmd.Version)
	if err != nil {
		exitCode = -1
		output = err.Error()
	}

	outCh <- &pb.AgentEvent{
		Payload: &pb.AgentEvent_AckCommand{
			AckCommand: &pb.AckCommand{
				CommandId: cmd.CommandId,
				ExitCode:  exitCode,
				Output:    output,
			},
		},
	}
}

func executeScriptCommand(cmd *pb.ScriptCommand, outCh chan<- *pb.AgentEvent) {
	envVars := make(map[string]string, len(cmd.EnvVars))
	for k, v := range cmd.EnvVars {
		envVars[k] = v
	}
	exitCode, output, err := script.Execute(cmd.ScriptBody, envVars)
	if err != nil {
		exitCode = -1
		output = err.Error()
	}

	outCh <- &pb.AgentEvent{
		Payload: &pb.AgentEvent_AckCommand{
			AckCommand: &pb.AckCommand{
				CommandId: cmd.CommandId,
				ExitCode:  exitCode,
				Output:    output,
			},
		},
	}
}
