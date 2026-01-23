//go:build linux || darwin

package shell

import (
	"fmt"
	"os"
	"os/exec"
	"sync"
	"sync/atomic"
	"time"

	"github.com/creack/pty"
)

type unixSession struct {
	cmd      *exec.Cmd
	ptmx     *os.File
	out      chan []byte
	done     chan struct{}
	exitCode atomic.Int32
}

func detectShell() string {
	if path, err := exec.LookPath("bash"); err == nil {
		return path
	}
	return "/bin/sh"
}

func Start(id string, cols, rows uint32) (Session, error) {
	cmd := exec.Command(detectShell())
	cmd.Env = append(os.Environ(), "TERM=xterm-256color")

	ptmx, err := pty.StartWithSize(cmd, &pty.Winsize{Cols: uint16(cols), Rows: uint16(rows)})
	if err != nil {
		return nil, fmt.Errorf("starting pty: %w", err)
	}

	s := &unixSession{
		cmd:  cmd,
		ptmx: ptmx,
		out:  make(chan []byte, 32),
		done: make(chan struct{}),
	}

	go s.run()

	return s, nil
}

func (s *unixSession) run() {
	var wg sync.WaitGroup
	wg.Add(1)
	go func() {
		defer wg.Done()
		err := s.cmd.Wait()
		code := int32(0)
		if err != nil {
			if s.cmd.ProcessState != nil {
				code = int32(s.cmd.ProcessState.ExitCode())
			}
		}
		s.exitCode.Store(code)
	}()

	buf := make([]byte, 4096)
	for {
		n, err := s.ptmx.Read(buf)
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

func (s *unixSession) Write(p []byte) error {
	_, err := s.ptmx.Write(p)
	return err
}

func (s *unixSession) Resize(cols, rows uint32) error {
	return pty.Setsize(s.ptmx, &pty.Winsize{Cols: uint16(cols), Rows: uint16(rows)})
}

func (s *unixSession) Close() error {
	s.ptmx.Close()
	select {
	case <-s.done:
	case <-time.After(200 * time.Millisecond):
		if s.cmd.Process != nil {
			s.cmd.Process.Kill()
		}
		<-s.done
	}
	return nil
}

func (s *unixSession) Out() <-chan []byte  { return s.out }
func (s *unixSession) Done() <-chan struct{} { return s.done }
func (s *unixSession) ExitCode() int32     { return s.exitCode.Load() }
