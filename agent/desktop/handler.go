package desktop

import (
	"context"
	"fmt"
	"os"
	"sync"
	"time"

	pb "github.com/pulsermm/pulse-rmm/agent/gen/pulse/v1"
)

type Handler struct {
	mu       sync.Mutex
	startMu  sync.Mutex
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
	h.startMu.Lock()
	defer h.startMu.Unlock()

	sessionID := cmd.GetSessionId()
	fmt.Printf("[desktop] HandleStartSession: %s, TURN URLs: %v\n", sessionID, cmd.GetTurnUrls())

	// Cancel any existing sessions before starting a new one — prevents zombie
	// capture goroutines from previous failed/retried session attempts.
	h.mu.Lock()
	for id, s := range h.sessions {
		if cancel, ok := h.cancels[id]; ok {
			cancel()
		}
		s.Close()
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

	if err := startCapture(sess, ctx); err != nil {
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

	h.mu.Lock()
	h.sessions[sessionID] = sess
	h.cancels[sessionID] = cancel
	h.mu.Unlock()

	// Cancel capture when the browser closes the WebRTC connection without
	// an explicit EndSession command (e.g. tab close, network drop).
	sess.OnPeerConnectionClosed(func() {
		fmt.Printf("[desktop] peer connection closed, terminating session %s\n", sessionID)
		h.mu.Lock()
		delete(h.sessions, sessionID)
		delete(h.cancels, sessionID)
		h.mu.Unlock()
		cancel()
	})

	fmt.Printf("[desktop] Session created, waiting 500ms before ready signal\n")
	// Give browser time to be ready for WebRTC connection before sending ready signal
	time.Sleep(500 * time.Millisecond)

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
		// HandleOffer waits for ICE gathering; answer SDP includes all candidates (bundled ICE)
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
		return
	}

	fmt.Printf("[desktop] EndSession: terminating session %s\n", sessionID)
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
