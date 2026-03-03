package svc

import (
	"context"
	"fmt"
	"os"

	"github.com/kardianos/service"
)

// ServiceName is used by the OS service manager.
const ServiceName = "pulse-agent"

// program implements service.Interface so kardianos/service can manage us.
type program struct {
	run    func(ctx context.Context)
	cancel context.CancelFunc
}

func (p *program) Start(s service.Service) error {
	ctx, cancel := context.WithCancel(context.Background())
	p.cancel = cancel
	go p.run(ctx)
	return nil
}

func (p *program) Stop(s service.Service) error {
	if p.cancel != nil {
		p.cancel()
	}
	return nil
}

func newService(runFn func(ctx context.Context)) (service.Service, error) {
	prg := &program{run: runFn}
	cfg := &service.Config{
		Name:        ServiceName,
		DisplayName: "Pulse RMM Agent",
		Description: "Pulse RMM endpoint management agent",
		Arguments:   []string{"run"},
	}
	return service.New(prg, cfg)
}

// activeService holds the running service instance so Restart() can reach it.
var activeService service.Service

// Run starts the agent and blocks until the OS asks it to stop.
// On Linux this integrates with systemd; on Windows with the SCM.
func Run(runFn func(ctx context.Context)) error {
	s, err := newService(runFn)
	if err != nil {
		return fmt.Errorf("creating service: %w", err)
	}
	activeService = s
	return s.Run()
}

// Restart asks the OS service manager to restart the agent. Used by the
// autoupdate loop after a binary swap. Falls back to os.Exit(0) when not
// running as a managed service (e.g. interactive dev mode).
func Restart() {
	if activeService != nil {
		if err := activeService.Restart(); err == nil {
			return
		}
	}
	// interactive / dev mode: exit so the user or wrapper script can restart
	fmt.Println("[svc] restarting process for update")
	os.Exit(0)
}

// Install registers the current binary as a system service and enables it.
// Requires root on Linux or Administrator on Windows.
func Install() error {
	if err := checkPrivilege(); err != nil {
		return err
	}
	s, err := newService(func(ctx context.Context) {})
	if err != nil {
		return fmt.Errorf("creating service: %w", err)
	}
	if err := s.Install(); err != nil {
		return fmt.Errorf("installing service: %w", err)
	}
	fmt.Printf("Service %q installed.\n", ServiceName)
	return nil
}

// Uninstall stops and removes the system service registration.
// Requires root on Linux or Administrator on Windows.
func Uninstall() error {
	if err := checkPrivilege(); err != nil {
		return err
	}
	s, err := newService(func(ctx context.Context) {})
	if err != nil {
		return fmt.Errorf("creating service: %w", err)
	}
	if err := s.Stop(); err != nil {
		// service may not be running; continue anyway
		fmt.Printf("Note: stopping service returned: %v\n", err)
	}
	if err := s.Uninstall(); err != nil {
		return fmt.Errorf("uninstalling service: %w", err)
	}
	fmt.Printf("Service %q uninstalled.\n", ServiceName)
	return nil
}

// Status prints whether the service is running, stopped, or not installed.
func Status() error {
	s, err := newService(func(ctx context.Context) {})
	if err != nil {
		return fmt.Errorf("creating service: %w", err)
	}
	st, err := s.Status()
	if err != nil {
		return fmt.Errorf("querying service status: %w", err)
	}
	switch st {
	case service.StatusRunning:
		fmt.Println("running")
	case service.StatusStopped:
		fmt.Println("stopped")
	default:
		fmt.Println("not installed")
	}
	return nil
}
