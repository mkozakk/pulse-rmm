package shell

import (
	"fmt"
	"sync"

	pb "github.com/pulsermm/pulse-rmm/agent/gen/pulse/v1"
)

type Manager struct {
	mu       sync.Mutex
	sessions map[string]Session
	out      chan<- *pb.AgentEvent
}

func NewManager(out chan<- *pb.AgentEvent) *Manager {
	return &Manager{
		sessions: make(map[string]Session),
		out:      out,
	}
}

func (m *Manager) Open(id string, cols, rows uint32) error {
	sess, err := Start(id, cols, rows)
	if err != nil {
		return fmt.Errorf("starting session: %w", err)
	}

	m.mu.Lock()
	m.sessions[id] = sess
	m.mu.Unlock()

	go func() {
		for chunk := range sess.Out() {
			m.out <- &pb.AgentEvent{
				Payload: &pb.AgentEvent_ShellOutput{
					ShellOutput: &pb.ShellOutput{SessionId: id, Data: chunk},
				},
			}
		}
		m.out <- &pb.AgentEvent{
			Payload: &pb.AgentEvent_ShellExited{
				ShellExited: &pb.ShellExited{SessionId: id, ExitCode: sess.ExitCode()},
			},
		}
		m.mu.Lock()
		delete(m.sessions, id)
		m.mu.Unlock()
	}()

	return nil
}

func (m *Manager) Input(id string, data []byte) error {
	m.mu.Lock()
	sess, ok := m.sessions[id]
	m.mu.Unlock()
	if !ok {
		return fmt.Errorf("session %s not found", id)
	}
	return sess.Write(data)
}

func (m *Manager) Resize(id string, cols, rows uint32) error {
	m.mu.Lock()
	sess, ok := m.sessions[id]
	m.mu.Unlock()
	if !ok {
		return fmt.Errorf("session %s not found", id)
	}
	return sess.Resize(cols, rows)
}

func (m *Manager) Close(id string) {
	m.mu.Lock()
	sess, ok := m.sessions[id]
	if ok {
		delete(m.sessions, id)
	}
	m.mu.Unlock()
	if ok {
		sess.Close()
	}
}

func (m *Manager) CloseAll() {
	m.mu.Lock()
	sessions := m.sessions
	m.sessions = make(map[string]Session)
	m.mu.Unlock()
	for _, sess := range sessions {
		sess.Close()
	}
}
