//go:build windows

package shell

import (
	"context"
	"fmt"
	"os/exec"
	"sync"
	"sync/atomic"

	conptypkg "github.com/UserExistsError/conpty"
)

type windowsSession struct {
	cpty     *conptypkg.ConPty
	out      chan []byte
	done     chan struct{}
	exitCode atomic.Int32
}

func detectShell() string {
	if path, err := exec.LookPath("powershell.exe"); err == nil {
		return path
	}
	return "cmd.exe"
}

func Start(id string, cols, rows uint32) (Session, error) {
	cpty, err := conptypkg.Start(detectShell(), conptypkg.ConPtyDimensions(int(cols), int(rows)))
	if err != nil {
		return nil, fmt.Errorf("starting conpty: %w", err)
	}

	s := &windowsSession{
		cpty: cpty,
		out:  make(chan []byte, 32),
		done: make(chan struct{}),
	}

	go s.run()

	return s, nil
}

func (s *windowsSession) run() {
	var wg sync.WaitGroup
	wg.Add(1)
	go func() {
		defer wg.Done()
		code, _ := s.cpty.Wait(context.Background())
		s.exitCode.Store(int32(code))
	}()

	buf := make([]byte, 4096)
	for {
		n, err := s.cpty.Read(buf)
		if n > 0 {
			chunk := make([]byte, n)
			copy(chunk, buf[:n])
			s.out <- chunk
		}
		if err != nil {
			break
		}
	}

	wg.Wait()
	close(s.out)
	close(s.done)
}

func (s *windowsSession) Write(p []byte) error {
	_, err := s.cpty.Write(p)
	return err
}

func (s *windowsSession) Resize(cols, rows uint32) error {
	return s.cpty.Resize(int(cols), int(rows))
}

func (s *windowsSession) Close() error {
	s.cpty.Close()
	<-s.done
	return nil
}

func (s *windowsSession) Out() <-chan []byte   { return s.out }
func (s *windowsSession) Done() <-chan struct{} { return s.done }
func (s *windowsSession) ExitCode() int32      { return s.exitCode.Load() }
