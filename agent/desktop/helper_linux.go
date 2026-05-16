//go:build linux

package desktop

import (
	"context"
	"encoding/json"
	"fmt"
	"net"
	"os"
	"path/filepath"

	"github.com/pion/webrtc/v4"
	pb "github.com/pulsermm/pulse-rmm/agent/gen/pulse/v1"
)

// helperMsg is the IPC envelope between the system service and the user helper.
type helperMsg struct {
	Type       string   `json:"t"`
	SessionID  string   `json:"sid"`
	TurnURLs   []string `json:"turns,omitempty"`
	TurnSecret string   `json:"secret,omitempty"`
	SigType    string   `json:"stype,omitempty"`
	Payload    string   `json:"payload,omitempty"`
	Error      string   `json:"error,omitempty"`
}

func defaultHelperSocket() string {
	return fmt.Sprintf("/run/user/%d/pulse-agent-desktop.sock", os.Getuid())
}

// RunHelper runs the desktop capture helper inside the user's graphical session.
// It listens on a Unix socket and serves desktop sessions delegated by the
// system service. The socket path defaults to /run/user/<uid>/pulse-agent-desktop.sock.
func RunHelper(addr string) {
	if os.Getuid() == 0 {
		fmt.Fprintln(os.Stderr, "[desktop-helper] refusing to run as root — the helper must run inside the user's graphical session (with DISPLAY or WAYLAND_DISPLAY). If you used `sudo nohup pulse-agent --desktop-helper`, rerun without sudo.")
		return
	}
	if addr == "" {
		addr = defaultHelperSocket()
	}
	if err := os.MkdirAll(filepath.Dir(addr), 0700); err != nil {
		fmt.Fprintf(os.Stderr, "[desktop-helper] mkdir: %v\n", err)
		return
	}
	_ = os.Remove(addr)

	l, err := net.Listen("unix", addr)
	if err != nil {
		fmt.Fprintf(os.Stderr, "[desktop-helper] listen %s: %v\n", addr, err)
		return
	}
	defer os.Remove(addr)
	// Resolve Wayland early so env is backfilled for child ffmpeg invocations
	// in every session spawned by this helper.
	wayland := isWaylandSession()
	fmt.Printf("[desktop-helper] listening on %s wayland=%v %s\n", addr, wayland, describeSessionEnv())

	for {
		conn, err := l.Accept()
		if err != nil {
			return
		}
		go serveHelperConn(conn)
	}
}

func serveHelperConn(conn net.Conn) {
	defer conn.Close()
	enc := json.NewEncoder(conn)
	dec := json.NewDecoder(conn)

	var start helperMsg
	if err := dec.Decode(&start); err != nil || start.Type != "start" {
		return
	}
	sessionID := start.SessionID

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	send := func(ev *pb.AgentEvent) {
		sig, ok := ev.Payload.(*pb.AgentEvent_DesktopSignal)
		if !ok {
			return
		}
		_ = enc.Encode(helperMsg{
			Type:      "signal",
			SessionID: sessionID,
			SigType:   sig.DesktopSignal.GetType(),
			Payload:   sig.DesktopSignal.GetPayload(),
		})
	}

	sess, err := NewSession(sessionID, start.TurnURLs, start.TurnSecret)
	if err != nil {
		_ = enc.Encode(helperMsg{Type: "ready", SessionID: sessionID, Error: err.Error()})
		return
	}

	sess.OnICECandidate(func(c *webrtc.ICECandidate) {
		if c == nil {
			return
		}
		b, err := json.Marshal(c.ToJSON())
		if err != nil {
			return
		}
		send(&pb.AgentEvent{
			Payload: &pb.AgentEvent_DesktopSignal{
				DesktopSignal: &pb.DesktopSignalMessage{
					SessionId: sessionID,
					Type:      "candidate",
					Payload:   string(b),
				},
			},
		})
	})

	if err := startCapture(sess, ctx); err != nil {
		sess.Close()
		_ = enc.Encode(helperMsg{Type: "ready", SessionID: sessionID, Error: err.Error()})
		return
	}

	sess.OnPeerConnectionClosed(func() {
		_ = enc.Encode(helperMsg{Type: "peer_closed", SessionID: sessionID})
		cancel()
	})

	_ = enc.Encode(helperMsg{Type: "ready", SessionID: sessionID})

	for {
		var msg helperMsg
		if err := dec.Decode(&msg); err != nil {
			return
		}
		switch msg.Type {
		case "signal":
			switch msg.SigType {
			case "offer":
				answer, err := sess.HandleOffer(msg.Payload)
				if err != nil {
					_ = enc.Encode(helperMsg{Type: "signal", SessionID: sessionID, SigType: "answer", Error: err.Error()})
					return
				}
				_ = enc.Encode(helperMsg{Type: "signal", SessionID: sessionID, SigType: "answer", Payload: answer})
			case "candidate":
				_ = sess.AddICECandidate(msg.Payload)
			}
		case "end":
			sess.Close()
			return
		}
	}
}
