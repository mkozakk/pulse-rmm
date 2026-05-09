package main

import (
	"context"
	"fmt"
	"os"
	"os/signal"
	"syscall"
	"time"

	pb "github.com/pulsermm/pulse-rmm/agent/gen/pulse/v1"
	"github.com/pulsermm/pulse-rmm/agent/desktop"
	"github.com/pulsermm/pulse-rmm/agent/internal/control"
	"github.com/pulsermm/pulse-rmm/agent/internal/enrolment"
	"github.com/pulsermm/pulse-rmm/agent/internal/metrics"
	"github.com/pulsermm/pulse-rmm/agent/internal/script"
	"github.com/pulsermm/pulse-rmm/agent/internal/shell"
	"github.com/pulsermm/pulse-rmm/agent/internal/software"
	"github.com/pulsermm/pulse-rmm/agent/internal/store"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
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

	gatewayConn, err := grpc.NewClient(gatewayAddr, grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error dialing gateway for agent service: %v\n", err)
		os.Exit(1)
	}
	defer gatewayConn.Close()
	agentClient := pb.NewAgentServiceClient(gatewayConn)

	ctx, cancel := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer cancel()

	fmt.Println("[main] Starting goroutines")
	go runHeartbeat(ctx, metricClient, endpointID)
	fmt.Println("[main] Started heartbeat")
	go runMetrics(ctx, metricClient, endpointID)
	fmt.Println("[main] Started metrics")
	go runSoftwareScan(ctx, endpointID, agentClient)
	fmt.Println("[main] Started software scan")
	go runControlStream(ctx, endpointID, gatewayAddr, agentClient)
	fmt.Println("[main] Started control stream")

	<-ctx.Done()
	fmt.Println("Shutting down")
}

func runControlStream(ctx context.Context, endpointID, gatewayAddr string, agentClient pb.AgentServiceClient) {
	inCh := make(chan *pb.GatewayCommand, 16)
	outCh := make(chan *pb.AgentEvent, 16)

	shellMgr := shell.NewManager(outCh)
	defer shellMgr.CloseAll()

	desktopHandler := desktop.NewHandler()

	go func() {
		for {
			select {
			case cmd, ok := <-inCh:
				if !ok {
					return
				}
				dispatchCmd(shellMgr, desktopHandler, cmd, outCh, agentClient, endpointID)
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

func dispatchCmd(mgr *shell.Manager, deskHandler *desktop.Handler, cmd *pb.GatewayCommand, outCh chan<- *pb.AgentEvent, agentClient pb.AgentServiceClient, endpointID string) {
	send := func(e *pb.AgentEvent) { outCh <- e }
	switch p := cmd.Payload.(type) {
	case *pb.GatewayCommand_StartDesktopSession:
		go deskHandler.HandleStartSession(p.StartDesktopSession, send)
	case *pb.GatewayCommand_EndDesktopSession:
		deskHandler.HandleEndSession(p.EndDesktopSession)
	case *pb.GatewayCommand_DesktopSignal:
		go deskHandler.HandleSignal(p.DesktopSignal, send)
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
		go executeSoftwareCommand(p.SoftwareCommand, outCh, agentClient, endpointID)
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

func runSoftwareScan(ctx context.Context, endpointID string, client pb.AgentServiceClient) {
	pushSoftwareList(ctx, endpointID, client)

	ticker := time.NewTicker(10 * time.Minute)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			pushSoftwareList(ctx, endpointID, client)
		}
	}
}

func pushSoftwareList(ctx context.Context, endpointID string, client pb.AgentServiceClient) {
	items, err := software.Scan()
	if err != nil {
		fmt.Fprintf(os.Stderr, "software scan error: %v\n", err)
		return
	}

	fmt.Printf("[agent] Scanned %d software items\n", len(items))
	if len(items) > 0 {
		fmt.Printf("[agent] Sample items: %s, %s, ...\n", items[0].Name, items[1].Name)
		// Check if hello is in the list
		for _, item := range items {
			if item.Name == "hello" {
				fmt.Printf("[agent] ✓ FOUND HELLO IN SCAN: version=%s\n", item.Version)
				break
			}
		}
	}

	pbItems := make([]*pb.SoftwareItem, 0, len(items))
	for _, item := range items {
		pbItems = append(pbItems, &pb.SoftwareItem{
			Name:    item.Name,
			Version: item.Version,
			Source:  item.Source,
		})
	}

	fmt.Printf("[agent] Pushing %d items to server\n", len(pbItems))
	_, err = client.PushSoftwareList(ctx, &pb.SoftwareList{
		EndpointId: endpointID,
		Items:      pbItems,
	})
	if err != nil {
		fmt.Fprintf(os.Stderr, "software push error: %v\n", err)
		return
	}
	fmt.Printf("[agent] Successfully pushed %d software items\n", len(items))
}

func executeSoftwareCommand(cmd *pb.SoftwareCommand, outCh chan<- *pb.AgentEvent, agentClient pb.AgentServiceClient, endpointID string) {
	fmt.Printf("[agent] Executing software command: action=%s, name=%s, version=%s\n", cmd.Action, cmd.Name, cmd.Version)

	// Scan before
	itemsBefore, err := software.Scan()
	if err != nil {
		fmt.Printf("[agent] ERROR scanning before: %v\n", err)
	}
	fmt.Printf("[agent] Packages BEFORE %s: %d items\n", cmd.Action, len(itemsBefore))
	for _, item := range itemsBefore {
		if item.Name == cmd.Name {
			fmt.Printf("[agent]   - Found target package BEFORE: %s v%s\n", item.Name, item.Version)
		}
	}

	exitCode, output, err := software.Execute(cmd.Action, cmd.Name, cmd.Version)
	if err != nil {
		exitCode = -1
		output = err.Error()
	}

	fmt.Printf("[agent] Command executed: exitCode=%d\n", exitCode)
	fmt.Printf("[agent] Command output:\n%s\n", output)

	// Scan after
	itemsAfter, err := software.Scan()
	if err != nil {
		fmt.Printf("[agent] ERROR scanning after: %v\n", err)
	}
	fmt.Printf("[agent] Packages AFTER %s: %d items\n", cmd.Action, len(itemsAfter))
	for _, item := range itemsAfter {
		if item.Name == cmd.Name {
			fmt.Printf("[agent]   - Found target package AFTER: %s v%s\n", item.Name, item.Version)
		}
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

	fmt.Printf("[agent] Pushing updated software list after %s command\n", cmd.Action)
	pushSoftwareList(context.Background(), endpointID, agentClient)
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
