package main

import (
	"context"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"time"

	pb "github.com/pulsermm/pulse-rmm/agent/gen/pulse/v1"
	"github.com/pulsermm/pulse-rmm/agent/desktop"
	"github.com/pulsermm/pulse-rmm/agent/internal/config"
	"github.com/pulsermm/pulse-rmm/agent/internal/control"
	"github.com/pulsermm/pulse-rmm/agent/internal/enrolment"
	"github.com/pulsermm/pulse-rmm/agent/internal/files"
	"github.com/pulsermm/pulse-rmm/agent/internal/metrics"
	"github.com/pulsermm/pulse-rmm/agent/internal/script"
	"github.com/pulsermm/pulse-rmm/agent/internal/shell"
	"github.com/pulsermm/pulse-rmm/agent/internal/software"
	"github.com/pulsermm/pulse-rmm/agent/internal/store"
	"github.com/pulsermm/pulse-rmm/agent/internal/svc"
	"github.com/pulsermm/pulse-rmm/agent/internal/update"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
)

// Version is overridden at build time with -ldflags "-X main.Version=x.y.z"
var Version = "dev"

func main() {
	args := os.Args[1:]

	switch {
	case len(args) >= 1 && args[0] == "--desktop-helper":
		addr := ""
		if len(args) >= 2 {
			addr = args[1]
		}
		desktop.RunHelper(addr)
	case len(args) >= 2 && args[0] == "service":
		runServiceCmd(args[1])
	case len(args) == 1 && args[0] == "run":
		runViaServiceManager()
	case len(args) == 0 || (len(args) == 1 && args[0] == "run"):
		runViaServiceManager()
	default:
		fmt.Fprintf(os.Stderr, "Usage: %s [run | service install|uninstall|status]\n", os.Args[0])
		os.Exit(1)
	}
}

// runServiceCmd handles the "service <action>" subcommands.
func runServiceCmd(action string) {
	var err error
	switch action {
	case "install":
		err = svc.Install()
	case "uninstall":
		err = svc.Uninstall()
	case "status":
		err = svc.Status()
	default:
		fmt.Fprintf(os.Stderr, "Unknown service action %q. Use: install, uninstall, status\n", action)
		os.Exit(1)
	}
	if err != nil {
		fmt.Fprintf(os.Stderr, "error: %v\n", err)
		os.Exit(1)
	}
}

// runViaServiceManager hands control to kardianos/service which handles both
// interactive runs and proper service lifecycle (systemd / Windows SCM).
func runViaServiceManager() {
	if err := svc.Run(runAgent); err != nil {
		fmt.Fprintf(os.Stderr, "error: %v\n", err)
		os.Exit(1)
	}
}

// setupAgentLog redirects stdout/stderr and the stdlib logger to
// <dataDir>/logs/agent.log so every fmt.Printf/log.Printf in the agent ends
// up on disk. The original stdout is kept as a tee so interactive runs still
// show output in the console.
func setupAgentLog(dataDir string) {
	logDir := filepath.Join(dataDir, "logs")
	if err := os.MkdirAll(logDir, 0755); err != nil {
		fmt.Fprintf(os.Stderr, "agent log: could not create %s: %v\n", logDir, err)
		return
	}
	logPath := filepath.Join(logDir, "agent.log")
	f, err := os.OpenFile(logPath, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0644)
	if err != nil {
		fmt.Fprintf(os.Stderr, "agent log: could not open %s: %v\n", logPath, err)
		return
	}
	origStdout := os.Stdout
	os.Stdout = f
	os.Stderr = f
	log.SetOutput(f)
	log.SetFlags(log.LstdFlags | log.Lmicroseconds)
	fmt.Fprintf(origStdout, "agent logging to %s\n", logPath)
	fmt.Fprintf(f, "\n=== agent started pid=%d version=%s ===\n", os.Getpid(), Version)
}

// runAgent is the main agent logic. ctx is cancelled when the service is
// asked to stop (SIGTERM, systemd stop, SCM stop).
func runAgent(ctx context.Context) {
	cfgPath := config.DefaultPath()
	cfg, err := config.Load(cfgPath)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error loading config from %s: %v\n", cfgPath, err)
		fmt.Fprintf(os.Stderr, "Create %s with at minimum:\n  api_url: <backend address>\n  enrolment_token: <token>\n", cfgPath)
		os.Exit(1)
	}

	store.Init(cfg.DataDir)
	setupAgentLog(cfg.DataDir)

	grpcAddr := cfg.GRPCAddr
	if grpcAddr == "" {
		grpcAddr = "localhost:9090"
	}

	privKey, err := store.LoadOrGenerateKey()
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		os.Exit(1)
	}

	endpointID, err := store.LoadEndpointID()
	if err != nil {
		if cfg.EnrolmentToken == "" {
			fmt.Fprintf(os.Stderr, "Error: no endpoint identity found and no enrolment_token in config\n")
			os.Exit(1)
		}
		endpointID, err = enrolment.Enrol(ctx, cfg.EnrolmentToken, grpcAddr, cfg.APIURL, privKey)
		if err != nil {
			fmt.Fprintf(os.Stderr, "Error: %v\n", err)
			os.Exit(1)
		}
		if err := store.SaveEndpointID(endpointID); err != nil {
			fmt.Fprintf(os.Stderr, "Error: %v\n", err)
			os.Exit(1)
		}
		fmt.Printf("Enrolled: %s\n", endpointID)
		if err := cfg.RemoveToken(); err != nil {
			fmt.Fprintf(os.Stderr, "Warning: could not remove token from config: %v\n", err)
		}
	} else {
		if cfg.EnrolmentToken != "" {
			fmt.Fprintf(os.Stderr, "Warning: enrolment_token in config is stale (already enrolled); removing it\n")
			_ = cfg.RemoveToken()
		}
		fmt.Printf("Already enrolled: %s\n", endpointID)
	}

	grpcCreds := insecure.NewCredentials()

	metricClient, err := metrics.NewClient(grpcAddr, grpcAddr, grpc.WithTransportCredentials(grpcCreds))
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		os.Exit(1)
	}
	defer metricClient.Close()

	gatewayConn, err := grpc.NewClient(grpcAddr, grpc.WithTransportCredentials(grpcCreds))
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error dialing gateway: %v\n", err)
		os.Exit(1)
	}
	defer gatewayConn.Close()
	agentClient := pb.NewAgentServiceClient(gatewayConn)
	fileClient := pb.NewFileServiceClient(gatewayConn)

	// heartbeatOK is closed on the first successful heartbeat so the post-update
	// verifier knows the new binary is healthy.
	heartbeatOK := make(chan struct{})

	// Check whether we just finished an autoupdate swap.
	if pending, _ := update.LoadPending(cfg.DataDir); pending != nil {
		binPath, _ := os.Executable()
		go update.VerifyOrRollback(ctx, pending, binPath, cfg.DataDir,
			heartbeatOK, svc.Restart, cfg.APIURL, endpointID)
	}

	updater := &update.Updater{
		APIURL:         cfg.APIURL,
		DataDir:        cfg.DataDir,
		CurrentVersion: Version,
		RestartFn:      svc.Restart,
	}

	fmt.Println("[agent] Starting goroutines")
	go runHeartbeat(ctx, metricClient, endpointID, heartbeatOK)
	go runMetrics(ctx, metricClient, endpointID)
	go runSoftwareScan(ctx, endpointID, agentClient)
	go runControlStream(ctx, endpointID, grpcAddr, agentClient, fileClient, grpc.WithTransportCredentials(grpcCreds))
	go updater.Start(ctx)

	<-ctx.Done()
	fmt.Println("[agent] Shutting down")
}

func runControlStream(ctx context.Context, endpointID, gatewayAddr string, agentClient pb.AgentServiceClient, fileClient pb.FileServiceClient, connOpt grpc.DialOption) {
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
				dispatchCmd(ctx, shellMgr, desktopHandler, cmd, outCh, agentClient, fileClient, endpointID)
			case <-ctx.Done():
				return
			}
		}
	}()

	backoff := 250 * time.Millisecond
	for ctx.Err() == nil {
		err := control.Run(ctx, endpointID, gatewayAddr, "0.5.0", inCh, outCh, connOpt)
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

func dispatchCmd(ctx context.Context, mgr *shell.Manager, deskHandler *desktop.Handler, cmd *pb.GatewayCommand, outCh chan<- *pb.AgentEvent, agentClient pb.AgentServiceClient, fileClient pb.FileServiceClient, endpointID string) {
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
	case *pb.GatewayCommand_ListDir:
		go handleListDir(p.ListDir, outCh)
	case *pb.GatewayCommand_FileDownload:
		go handleFileDownload(ctx, p.FileDownload, fileClient, outCh)
	case *pb.GatewayCommand_FileUpload:
		go handleFileUpload(ctx, p.FileUpload, fileClient, outCh)
	}
}

func handleListDir(c *pb.ListDirCommand, outCh chan<- *pb.AgentEvent) {
	entries, err := files.ListDir(c.Path)
	res := &pb.ListDirResult{RequestId: c.RequestId, Path: c.Path, Entries: entries}
	if err != nil {
		res.Error = err.Error()
	}
	outCh <- &pb.AgentEvent{Payload: &pb.AgentEvent_ListDirResult{ListDirResult: res}}
}

func handleFileDownload(ctx context.Context, c *pb.FileDownloadCommand, fc pb.FileServiceClient, outCh chan<- *pb.AgentEvent) {
	n, err := files.SendFile(ctx, fc, c.TransferId, c.Path)
	done := &pb.FileTransferDone{TransferId: c.TransferId, Bytes: n}
	if err != nil {
		done.Error = err.Error()
	}
	outCh <- &pb.AgentEvent{Payload: &pb.AgentEvent_FileTransferDone{FileTransferDone: done}}
}

func handleFileUpload(ctx context.Context, c *pb.FileUploadCommand, fc pb.FileServiceClient, outCh chan<- *pb.AgentEvent) {
	n, err := files.ReceiveFile(ctx, fc, c.TransferId, c.DestPath)
	done := &pb.FileTransferDone{TransferId: c.TransferId, Bytes: n}
	if err != nil {
		done.Error = err.Error()
	}
	outCh <- &pb.AgentEvent{Payload: &pb.AgentEvent_FileTransferDone{FileTransferDone: done}}
}

func runHeartbeat(ctx context.Context, client *metrics.Client, endpointID string, okOnce chan struct{}) {
	ticker := time.NewTicker(30 * time.Second)
	defer ticker.Stop()
	signalled := false

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			if err := client.Heartbeat(ctx, endpointID); err != nil {
				fmt.Fprintf(os.Stderr, "heartbeat error: %v\n", err)
			} else if !signalled {
				close(okOnce)
				signalled = true
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
			for _, s := range samples {
				fmt.Printf("[metrics] %s=%.1f%%\n", s.Type, s.Value)
			}
			if err := client.PushMetrics(ctx, endpointID, samples); err != nil {
				fmt.Fprintf(os.Stderr, "metrics push error: %v\n", err)
			} else {
				fmt.Printf("[metrics] pushed %d samples for endpoint %s\n", len(samples), endpointID)
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
			Name:     item.Name,
			Version:  item.Version,
			Source:   item.Source,
			Id:       item.ID,
			UpdateTo: item.UpdateTo,
			IsStore:  item.IsStore,
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

	exitCode, output, err := software.Execute(cmd.Action, cmd.Name, cmd.Version, cmd.Id)
	if err != nil {
		exitCode = -1
		output = err.Error()
	}

	fmt.Printf("[agent] Command executed: exitCode=%d\n", exitCode)
	fmt.Printf("[agent] Command output:\n%s\n", output)

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
