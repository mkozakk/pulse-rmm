package desktop

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"sync"

	"github.com/pion/webrtc/v4"
	pb "github.com/pulsermm/pulse-rmm/agent/gen/pulse/v1"
)

type Handler struct {
	mu         sync.Mutex
	startMu    sync.Mutex
	sessions   map[string]*DesktopSession
	cancels    map[string]context.CancelFunc
	endedBeforeStart map[string]struct{}
}

func NewHandler() *Handler {
	return &Handler{
		sessions:   make(map[string]*DesktopSession),
		cancels:    make(map[string]context.CancelFunc),
		endedBeforeStart: make(map[string]struct{}),
	}
}

func (h *Handler) HandleStartSession(cmd *pb.StartDesktopSessionCommand, send func(*pb.AgentEvent)) {
	h.startMu.Lock()
	defer h.startMu.Unlock()

	sessionID := cmd.GetSessionId()

	h.mu.Lock()
	if _, wasEnded := h.endedBeforeStart[sessionID]; wasEnded {
		delete(h.endedBeforeStart, sessionID)
		h.mu.Unlock()
		fmt.Printf("[desktop] HandleStartSession: %s rejected (ended before start)\n", sessionID)
		return
	}
	h.mu.Unlock()

	fmt.Printf("[desktop] HandleStartSession: %s, TURN URLs: %v\n", sessionID, cmd.GetTurnUrls())

	h.mu.Lock()
	for id, s := range h.sessions {
		if cancel, ok := h.cancels[id]; ok {
			cancel()
		}
		if s != nil {
			s.Close()
		}
		delete(h.sessions, id)
		delete(h.cancels, id)
	}
	h.mu.Unlock()

	sess, err := NewSession(sessionID, cmd.GetTurnUrls(), cmd.GetTurnSecret())
	if err != nil {
		fmt.Printf("[desktop] Failed to create session: %v\n", err)
		sendSessionReady(send, sessionID, fmt.Sprintf("failed to create session: %v", err))
		return
	}

	ctx, cancel := context.WithCancel(context.Background())

	h.mu.Lock()
	h.sessions[sessionID] = sess
	h.cancels[sessionID] = cancel
	h.mu.Unlock()

	// Implement Trickle ICE: send generated candidates to frontend
	sess.pc.OnICECandidate(func(c *webrtc.ICECandidate) {
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
		h.mu.Lock()
		delete(h.sessions, sessionID)
		delete(h.cancels, sessionID)
		h.mu.Unlock()
		cancel()
		sess.Close()
		errMsg := err.Error()
		if err == ErrWaylandNotSupported {
			errMsg = "wayland_not_supported"
		}
		fmt.Printf("[desktop] Capture failed: %v\n", err)
		sendSessionReady(send, sessionID, errMsg)
		return
	}

	sess.OnPeerConnectionClosed(func() {
		fmt.Printf("[desktop] peer connection closed, terminating session %s\n", sessionID)
		h.mu.Lock()
		delete(h.sessions, sessionID)
		delete(h.cancels, sessionID)
		h.mu.Unlock()
		cancel()
		sess.Close()
	})

	if ctx.Err() != nil {
		fmt.Printf("[desktop] Session %s cancelled before ready signal\n", sessionID)
		return
	}

	sendSessionReady(send, sessionID, "")
	fmt.Printf("[desktop] Session ready signal sent: %s\n", sessionID)
}

func (h *Handler) HandleSignal(msg *pb.DesktopSignalMessage, send func(*pb.AgentEvent)) {
	sessionID := msg.GetSessionId()
	fmt.Printf("[desktop] HandleSignal: %s, type=%s\n", sessionID, msg.GetType())

	h.mu.Lock()
	sess := h.sessions[sessionID]
	h.mu.Unlock()

	if sess == nil {
		fmt.Fprintf(os.Stderr, "desktop: signal for unknown session %s\n", sessionID)
		return
	}

	switch msg.GetType() {
	case "offer":
		fmt.Printf("[desktop] Processing offer for session %s\n", sessionID)
		answer, err := sess.HandleOffer(msg.GetPayload())
		if err != nil {
			fmt.Fprintf(os.Stderr, "desktop: HandleOffer for %s: %v\n", sessionID, err)
			return
		}
		fmt.Printf("[desktop] Sending answer for session %s\n", sessionID)
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
		fmt.Printf("[desktop] Processing ICE candidate for session %s\n", sessionID)
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

	if cancel == nil && sess == nil {
		fmt.Printf("[desktop] EndSession: session %s not found (already ended)\n", sessionID)
		h.mu.Lock()
		h.endedBeforeStart[sessionID] = struct{}{}
		if len(h.endedBeforeStart) > 100 {
			h.endedBeforeStart = make(map[string]struct{})
		}
		h.mu.Unlock()
		return
	}

	fmt.Printf("[desktop] EndSession: terminating session %s\n", sessionID)
	if cancel != nil {
		cancel()
	}
	if sess != nil {
		sess.Close()
	}
	h.mu.Lock()
	h.endedBeforeStart[sessionID] = struct{}{}
	if len(h.endedBeforeStart) > 100 {
		h.endedBeforeStart = make(map[string]struct{})
	}
	h.mu.Unlock()
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
