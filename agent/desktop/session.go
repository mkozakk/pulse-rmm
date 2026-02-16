package desktop

import (
	"crypto/hmac"
	"crypto/sha1"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"runtime"
	"time"

	"github.com/pion/webrtc/v4"
)

var ErrWaylandNotSupported = errors.New("wayland is not supported")

type DesktopSession struct {
	sessionID   string
	pc          *webrtc.PeerConnection
	videoTrack  webrtc.TrackLocal
	injector    InputInjector
	rateLimiter *rateLimiter
}

func NewSession(sessionID string, turnURLs []string, turnSecret string) (*DesktopSession, error) {
	m := &webrtc.MediaEngine{}

	// Windows: try H264 (with fallback to VP9), Linux: use VP9
	var codecMimeType string
	var payloadType webrtc.PayloadType
	var fmtpLine string

	if runtime.GOOS == "windows" {
		// Will determine H264 vs VP9 in startCapture() based on codec availability
		// For now, register H264 as primary
		codecMimeType = webrtc.MimeTypeH264
		payloadType = 102
		fmtpLine = "level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e01f"
		fmt.Println("[desktop] Registering H264 codec for Windows (will fallback to VP9 if unavailable)")
	} else {
		codecMimeType = webrtc.MimeTypeVP9
		payloadType = 98
		fmtpLine = ""
		fmt.Println("[desktop] Registering VP9 codec for Linux")
	}

	if err := m.RegisterCodec(webrtc.RTPCodecParameters{
		RTPCodecCapability: webrtc.RTPCodecCapability{
			MimeType:    codecMimeType,
			ClockRate:   90000,
			SDPFmtpLine: fmtpLine,
		},
		PayloadType: payloadType,
	}, webrtc.RTPCodecTypeVideo); err != nil {
		return nil, fmt.Errorf("registering codec: %w", err)
	}

	api := webrtc.NewAPI(webrtc.WithMediaEngine(m))

	config := webrtc.Configuration{}
	if len(turnURLs) > 0 && turnSecret != "" {
		username, credential := generateTurnCredentials(sessionID, turnSecret)
		config.ICEServers = []webrtc.ICEServer{{
			URLs:           turnURLs,
			Username:       username,
			Credential:     credential,
			CredentialType: webrtc.ICECredentialTypePassword,
		}}
	}

	pc, err := api.NewPeerConnection(config)
	if err != nil {
		return nil, fmt.Errorf("creating peer connection: %w", err)
	}

	injector, err := newInputInjector()
	if err != nil {
		// non-fatal: log and continue with noop injector
		fmt.Fprintf(os.Stderr, "desktop: input injector unavailable: %v\n", err)
		injector = &noopInjector{}
	}

	sess := &DesktopSession{
		sessionID:   sessionID,
		pc:          pc,
		injector:    injector,
		rateLimiter: newRateLimiter(60),
	}
	sess.registerDataChannelHandler()
	return sess, nil
}

func (s *DesktopSession) registerDataChannelHandler() {
	s.pc.OnDataChannel(func(dc *webrtc.DataChannel) {
		switch dc.Label() {
		case "input":
			dc.OnMessage(func(msg webrtc.DataChannelMessage) {
				if !s.rateLimiter.allow() {
					return
				}
				ev, err := parseInputEvent(msg.Data)
				if err != nil {
					return
				}
				s.dispatchInputEvent(ev)
			})
		case "file-transfer":
			homeDir, err := os.UserHomeDir()
			if err != nil {
				homeDir = os.TempDir()
			}
			ft := newFileTransferHandler(
				func(text string) error { return dc.SendText(text) },
				func(data []byte) error { return dc.Send(data) },
				"/tmp/pulse-uploads",
				homeDir,
			)
			ft.HandleDataChannel(dc)
		}
	})
}

func (s *DesktopSession) addVideoTrack(track webrtc.TrackLocal) error {
	if _, err := s.pc.AddTrack(track); err != nil {
		return err
	}
	s.videoTrack = track
	return nil
}

func (s *DesktopSession) HandleOffer(sdpOffer string) (string, error) {
	fmt.Printf("[desktop] HandleOffer for %s: signalingState=%s\n", s.sessionID, s.pc.SignalingState())
	fmt.Printf("[desktop] Offer SDP (first 200 chars): %s...\n", truncate(sdpOffer, 200))

	// Set remote description from browser's offer
	if err := s.pc.SetRemoteDescription(webrtc.SessionDescription{
		Type: webrtc.SDPTypeOffer,
		SDP:  sdpOffer,
	}); err != nil {
		fmt.Printf("[desktop] Error setting remote description: %v\n", err)
		return "", fmt.Errorf("setting remote description: %w", err)
	}

	fmt.Printf("[desktop] Remote description set successfully, signalingState=%s\n", s.pc.SignalingState())

	// Create answer
	answer, err := s.pc.CreateAnswer(nil)
	if err != nil {
		fmt.Printf("[desktop] Error creating answer: %v\n", err)
		return "", fmt.Errorf("creating answer: %w", err)
	}

	fmt.Printf("[desktop] Answer created, waiting for ICE gathering...\n")

	// Wait for ICE gathering to complete
	gatherComplete := webrtc.GatheringCompletePromise(s.pc)
	if err := s.pc.SetLocalDescription(answer); err != nil {
		fmt.Printf("[desktop] Error setting local description: %v\n", err)
		return "", fmt.Errorf("setting local description: %w", err)
	}
	<-gatherComplete

	answerSDP := s.pc.LocalDescription().SDP
	fmt.Printf("[desktop] Answer ready, signalingState=%s\n", s.pc.SignalingState())
	fmt.Printf("[desktop] Answer SDP (first 200 chars): %s...\n", truncate(answerSDP, 200))

	return answerSDP, nil
}

func (s *DesktopSession) AddICECandidate(candidateJSON string) error {
	var init webrtc.ICECandidateInit
	if err := json.Unmarshal([]byte(candidateJSON), &init); err != nil {
		return fmt.Errorf("parsing ICE candidate: %w", err)
	}

	// Check if remote description is set (required before adding candidates)
	if s.pc.RemoteDescription() == nil {
		fmt.Printf("[desktop] AddICECandidate for %s: WARNING - remote description not set yet, candidate ignored\n", s.sessionID)
		return fmt.Errorf("remote description not set")
	}

	fmt.Printf("[desktop] AddICECandidate for %s: adding candidate (signalingState=%s)\n", s.sessionID, s.pc.SignalingState())
	if err := s.pc.AddICECandidate(init); err != nil {
		fmt.Printf("[desktop] AddICECandidate error: %v\n", err)
		return err
	}
	return nil
}

func (s *DesktopSession) dispatchInputEvent(ev inputEvent) {
	switch ev.Type {
	case "mousemove":
		s.injector.MouseMove(ev.X, ev.Y) //nolint:errcheck
	case "mousedown":
		s.injector.MouseButton(ev.Button, true) //nolint:errcheck
	case "mouseup":
		s.injector.MouseButton(ev.Button, false) //nolint:errcheck
	case "keydown":
		s.injector.KeyEvent(ev.KeyCode, true) //nolint:errcheck
	case "keyup":
		s.injector.KeyEvent(ev.KeyCode, false) //nolint:errcheck
	}
}

func (s *DesktopSession) Close() error {
	s.injector.Close() //nolint:errcheck
	return s.pc.Close()
}

func getCodecFmtpLine(mimeType string) string {
	if mimeType == webrtc.MimeTypeH264 {
		// H264 Constrained Baseline, Level 3.1
		// profile-level-id=42e01f: 0x42=Baseline, 0x00=no constraints, 0x1f=Level 3.1
		return "level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e01f"
	}
	return "" // VP9 doesn't need special fmtp line
}

func truncate(s string, maxLen int) string {
	if len(s) > maxLen {
		return s[:maxLen]
	}
	return s
}

func generateTurnCredentials(sessionID, secret string) (username, credential string) {
	expires := time.Now().Add(time.Hour).Unix()
	username = fmt.Sprintf("%d:%s", expires, sessionID)
	mac := hmac.New(sha1.New, []byte(secret))
	mac.Write([]byte(username))
	credential = base64.StdEncoding.EncodeToString(mac.Sum(nil))
	return
}
