//go:build !windows && !linux

package desktop

import (
	"context"
	"encoding/json"

	"github.com/pion/webrtc/v4"
	"github.com/pulsermm/pulse-rmm/agent/desktop/capture"
	pb "github.com/pulsermm/pulse-rmm/agent/gen/pulse/v1"
)

func startPlatformSession(sessionID string, turnURLs []string, turnSecret string, ctx context.Context, send func(*pb.AgentEvent)) (desktopSession, error) {
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

	if err := capture.Start(ctx, capture.Target{Logger: sess.log, LogFile: sess.logFile, AddTrack: sess.addVideoTrack}); err != nil {
		sess.Close()
		return nil, err
	}

	return sess, nil
}
