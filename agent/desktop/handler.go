package desktop

import (
	"context"
	"fmt"
	"os"
	"sync"

	pb "github.com/pulsermm/pulse-rmm/agent/gen/pulse/v1"
)

type Handler struct {
	mu       sync.Mutex
	sessions map[string]*DesktopSession
	cancels  map[string]context.CancelFunc
}

func NewHandler() *Handler {
	return &Handler{
		sessions: make(map[string]*DesktopSession),
		cancels:  make(map[string]context.CancelFunc),
	}
}

func (h *Handler) HandleStartSession(cmd *pb.StartDesktopSessionCommand, send func(*pb.AgentEvent)) {
	sessionID := cmd.GetSessionId()

	sess, err := NewSession(sessionID, cmd.GetTurnUrls(), cmd.GetTurnSecret())
	if err != nil {
		sendSessionReady(send, sessionID, fmt.Sprintf("failed to create session: %v", err))
		return
	}

	ctx, cancel := context.WithCancel(context.Background())

	if err := startCapture(sess, ctx); err != nil {
		cancel()
		sess.Close()
		errMsg := err.Error()
		if err == ErrWaylandNotSupported {
			errMsg = "wayland_not_supported"
		}
		sendSessionReady(send, sessionID, errMsg)
		return
	}

	h.mu.Lock()
	h.sessions[sessionID] = sess
	h.cancels[sessionID] = cancel
	h.mu.Unlock()

	sendSessionReady(send, sessionID, "")
}

func (h *Handler) HandleSignal(msg *pb.DesktopSignalMessage, send func(*pb.AgentEvent)) {
	sessionID := msg.GetSessionId()

	h.mu.Lock()
	sess := h.sessions[sessionID]
	h.mu.Unlock()

	if sess == nil {
		fmt.Fprintf(os.Stderr, "desktop: signal for unknown session %s\n", sessionID)
		return
	}

	switch msg.GetType() {
	case "offer":
		// HandleOffer waits for ICE gathering; answer SDP includes all candidates (bundled ICE)
		answer, err := sess.HandleOffer(msg.GetPayload())
		if err != nil {
			fmt.Fprintf(os.Stderr, "desktop: HandleOffer for %s: %v\n", sessionID, err)
			return
		}
		send(&pb.AgentEvent{
			Payload: &pb.AgentEvent_DesktopSignal{
				DesktopSignal: &pb.DesktopSignalMessage{
					SessionId: sessionID,
					Type:      "answer",
					Payload:   answer,
				},
			},
		})
	case "candidate":
		if err := sess.AddICECandidate(msg.GetPayload()); err != nil {
			fmt.Fprintf(os.Stderr, "desktop: AddICECandidate for %s: %v\n", sessionID, err)
		}
	}
}

func (h *Handler) HandleEndSession(cmd *pb.EndDesktopSessionCommand) {
	sessionID := cmd.GetSessionId()

	h.mu.Lock()
	sess := h.sessions[sessionID]
	cancel := h.cancels[sessionID]
	delete(h.sessions, sessionID)
	delete(h.cancels, sessionID)
	h.mu.Unlock()

	if cancel != nil {
		cancel()
	}
	if sess != nil {
		sess.Close()
	}
}

func sendSessionReady(send func(*pb.AgentEvent), sessionID, errMsg string) {
	send(&pb.AgentEvent{
		Payload: &pb.AgentEvent_SessionReady{
			SessionReady: &pb.SessionReadyEvent{
				SessionId: sessionID,
				Error:     errMsg,
			},
		},
	})
}
