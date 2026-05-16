//go:build linux

package desktop

import (
	"encoding/json"
	"fmt"
	"net"
	"path/filepath"
	"strconv"
	"strings"
	"sync"

	pb "github.com/pulsermm/pulse-rmm/agent/gen/pulse/v1"
)

// findHelperSocket returns the socket path of a running desktop helper
// owned by a real graphical-session user (UID >= 1000). Root's runtime dir
// (/run/user/0) is skipped — a helper there has no DISPLAY, no
// WAYLAND_DISPLAY, and no session bus, so handing it a session would just
// fail; this happens in practice when an admin runs the helper under
// `sudo` by mistake.
func findHelperSocket() string {
	matches, _ := filepath.Glob("/run/user/*/pulse-agent-desktop.sock")
	for _, m := range matches {
		// /run/user/<uid>/pulse-agent-desktop.sock
		parts := strings.Split(m, "/")
		if len(parts) < 4 {
			continue
		}
		uid, err := strconv.Atoi(parts[3])
		if err != nil || uid < 1000 {
			continue
		}
		return m
	}
	return ""
}

// helperProxy implements desktopSession by forwarding all calls to the user
// helper process over a Unix socket.
type helperProxy struct {
	conn      net.Conn
	enc       *json.Encoder
	dec       *json.Decoder
	mu        sync.Mutex
	sessionID string
	answerCh  chan string
	send      func(*pb.AgentEvent)
	closedMu  sync.Mutex
	onClosed  func()
	closed    bool
}

func connectToHelper(socketPath, sessionID string, turnURLs []string, turnSecret string, send func(*pb.AgentEvent)) (*helperProxy, error) {
	conn, err := net.Dial("unix", socketPath)
	if err != nil {
		return nil, fmt.Errorf("connecting to desktop helper: %w", err)
	}

	p := &helperProxy{
		conn:      conn,
		enc:       json.NewEncoder(conn),
		dec:       json.NewDecoder(conn),
		sessionID: sessionID,
		answerCh:  make(chan string, 1),
		send:      send,
	}

	if err := p.enc.Encode(helperMsg{
		Type:       "start",
		SessionID:  sessionID,
		TurnURLs:   turnURLs,
		TurnSecret: turnSecret,
	}); err != nil {
		conn.Close()
		return nil, fmt.Errorf("sending start to helper: %w", err)
	}

	var ready helperMsg
	if err := p.dec.Decode(&ready); err != nil {
		conn.Close()
		return nil, fmt.Errorf("reading ready from helper: %w", err)
	}
	if ready.Error != "" {
		conn.Close()
		return nil, fmt.Errorf("helper error: %s", ready.Error)
	}

	go p.readLoop()
	return p, nil
}

func (p *helperProxy) readLoop() {
	for {
		var msg helperMsg
		if err := p.dec.Decode(&msg); err != nil {
			p.triggerClosed()
			return
		}
		switch msg.Type {
		case "signal":
			if msg.SigType == "answer" {
				p.answerCh <- msg.Payload
			} else if msg.SigType == "candidate" {
				p.send(&pb.AgentEvent{
					Payload: &pb.AgentEvent_DesktopSignal{
						DesktopSignal: &pb.DesktopSignalMessage{
							SessionId: p.sessionID,
							Type:      "candidate",
							Payload:   msg.Payload,
						},
					},
				})
			}
		case "peer_closed":
			p.triggerClosed()
			return
		}
	}
}

func (p *helperProxy) triggerClosed() {
	p.closedMu.Lock()
	defer p.closedMu.Unlock()
	if p.closed {
		return
	}
	p.closed = true
	if p.onClosed != nil {
		p.onClosed()
	}
}

func (p *helperProxy) HandleOffer(sdp string) (string, error) {
	p.mu.Lock()
	err := p.enc.Encode(helperMsg{Type: "signal", SessionID: p.sessionID, SigType: "offer", Payload: sdp})
	p.mu.Unlock()
	if err != nil {
		return "", fmt.Errorf("sending offer to helper: %w", err)
	}
	return <-p.answerCh, nil
}

func (p *helperProxy) AddICECandidate(candidateJSON string) error {
	p.mu.Lock()
	defer p.mu.Unlock()
	return p.enc.Encode(helperMsg{Type: "signal", SessionID: p.sessionID, SigType: "candidate", Payload: candidateJSON})
}

func (p *helperProxy) OnPeerConnectionClosed(fn func()) {
	p.closedMu.Lock()
	defer p.closedMu.Unlock()
	p.onClosed = fn
}

func (p *helperProxy) Close() error {
	p.mu.Lock()
	_ = p.enc.Encode(helperMsg{Type: "end", SessionID: p.sessionID})
	p.mu.Unlock()
	return p.conn.Close()
}
