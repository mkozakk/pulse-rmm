//go:build linux

package desktop

import (
	"context"
	"encoding/json"
	"fmt"
	"os"

	"github.com/pion/webrtc/v4"
	"github.com/pulsermm/pulse-rmm/agent/desktop/capture"
	pb "github.com/pulsermm/pulse-rmm/agent/gen/pulse/v1"
)

func startPlatformSession(sessionID string, turnURLs []string, turnSecret string, ctx context.Context, send func(*pb.AgentEvent)) (desktopSession, error) {
	if sock := findHelperSocket(); sock != "" {
		fmt.Printf("[desktop] using user helper socket %s\n", sock)
		return connectToHelper(sock, sessionID, turnURLs, turnSecret, send)
	}

	fmt.Printf("[desktop] no user helper socket found (looked under /run/user/*/pulse-agent-desktop.sock), falling back to kmsgrab; XDG_SESSION_TYPE=%q\n", os.Getenv("XDG_SESSION_TYPE"))
	return startKmsSession(sessionID, turnURLs, turnSecret, ctx, send)
}

func startKmsSession(sessionID string, turnURLs []string, turnSecret string, ctx context.Context, send func(*pb.AgentEvent)) (desktopSession, error) {
	sess, err := NewSession(sessionID, turnURLs, turnSecret)
	if err != nil {
		return nil, err
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

	if err := capture.StartKMS(ctx, capture.Target{Logger: sess.log, LogFile: sess.logFile, AddTrack: sess.addVideoTrack}); err != nil {
		sess.Close()
		return nil, err
	}

	return sess, nil
}
