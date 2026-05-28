package desktop

import (
	"crypto/hmac"
	"crypto/sha1"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"os"
	"path/filepath"
	"runtime"
	"sync"
	"time"

	"github.com/pion/webrtc/v4"
	"github.com/pulsermm/pulse-rmm/agent/desktop/input"
	"github.com/pulsermm/pulse-rmm/agent/desktop/transfer"
)

var ErrWaylandNotSupported = errors.New("wayland is not supported")

var ErrNoActiveUserSession = errors.New("no active user session: remote desktop requires a logged-in user")

type DesktopSession struct {
	sessionID   string
	pc          *webrtc.PeerConnection
	videoTrack  webrtc.TrackLocal
	audioTrack  webrtc.TrackLocal
	injector    input.InputInjector
	rateLimiter *rateLimiter
	log         *log.Logger
	logFile     *os.File
	mu          sync.Mutex
	pendingICE  []webrtc.ICECandidateInit
	closeOnce   sync.Once
}

func sessionLogDir() string {
	if runtime.GOOS == "windows" {
		pd := os.Getenv("ProgramData")
		if pd == "" {
			pd = `C:\ProgramData`
		}
		return filepath.Join(pd, "pulse-agent", "logs")
	}
	// The system service runs as root and writes here. The user helper runs
	// as the desktop user and cannot, so fall back to a user-writable spot.
	const sys = "/var/log/pulse-agent"
	if err := os.MkdirAll(sys, 0755); err == nil {
		if f, err := os.CreateTemp(sys, ".probe-*"); err == nil {
			_ = f.Close()
			_ = os.Remove(f.Name())
			return sys
		}
	}
	if rt := os.Getenv("XDG_RUNTIME_DIR"); rt != "" {
		return filepath.Join(rt, "pulse-agent", "logs")
	}
	if home, err := os.UserHomeDir(); err == nil {
		return filepath.Join(home, ".local", "state", "pulse-agent", "logs")
	}
	return os.TempDir()
}

// tolerantWriter writes to all underlying writers but never returns an error,
// so a broken stdout cannot prevent the file write from happening.
type tolerantWriter struct{ ws []io.Writer }

func (t *tolerantWriter) Write(p []byte) (int, error) {
	for _, w := range t.ws {
		_, _ = w.Write(p)
	}
	return len(p), nil
}

func newSessionLogger(sessionID string) (*log.Logger, *os.File) {
	dir := sessionLogDir()
	if err := os.MkdirAll(dir, 0755); err != nil {
		dir = os.TempDir()
	}
	logPath := filepath.Join(dir, "desktop-"+sessionID+".log")
	f, err := os.Create(logPath)
	if err != nil {
		fmt.Fprintf(os.Stderr, "[desktop] could not create log file %s: %v\n", logPath, err)
		return log.New(os.Stdout, "[desktop] ", log.LstdFlags|log.Lmicroseconds), nil
	}
	fmt.Printf("[desktop] session log: %s\n", logPath)
	w := &tolerantWriter{ws: []io.Writer{os.Stdout, f}}
	lg := log.New(w, "[desktop] ", log.LstdFlags|log.Lmicroseconds)
	lg.Printf("session %s starting; go=%s os=%s arch=%s", sessionID, runtime.Version(), runtime.GOOS, runtime.GOARCH)
	_ = f.Sync()
	return lg, f
}

func NewSession(sessionID string, turnURLs []string, turnSecret string) (*DesktopSession, error) {
	m := &webrtc.MediaEngine{}

	logger, logFile := newSessionLogger(sessionID)

	if runtime.GOOS == "windows" {
		if err := m.RegisterCodec(webrtc.RTPCodecParameters{
			RTPCodecCapability: webrtc.RTPCodecCapability{
				MimeType:    webrtc.MimeTypeH264,
				ClockRate:   90000,
				SDPFmtpLine: "level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e02a",
			},
			PayloadType: 102,
		}, webrtc.RTPCodecTypeVideo); err != nil {
			return nil, fmt.Errorf("registering H264 codec: %w", err)
		}
		logger.Println("Registering H264 codec for Windows")
	} else {
		if err := m.RegisterCodec(webrtc.RTPCodecParameters{
			RTPCodecCapability: webrtc.RTPCodecCapability{
				MimeType:    webrtc.MimeTypeH264,
				ClockRate:   90000,
				SDPFmtpLine: "level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e02a",
			},
			PayloadType: 102,
		}, webrtc.RTPCodecTypeVideo); err != nil {
			return nil, fmt.Errorf("registering H264 codec: %w", err)
		}
		logger.Println("Registering H264 codec for Linux")
	}

	if err := m.RegisterCodec(webrtc.RTPCodecParameters{
		RTPCodecCapability: webrtc.RTPCodecCapability{
			MimeType:  webrtc.MimeTypeOpus,
			ClockRate: 48000,
			Channels:  2,
		},
		PayloadType: 111,
	}, webrtc.RTPCodecTypeAudio); err != nil {
		return nil, fmt.Errorf("registering Opus codec: %w", err)
	}

	api := webrtc.NewAPI(webrtc.WithMediaEngine(m))

	config := webrtc.Configuration{
		ICEServers: []webrtc.ICEServer{
			{URLs: []string{"stun:stun.l.google.com:19302"}},
		},
	}
	if len(turnURLs) > 0 && turnSecret != "" {
		username, credential := generateTurnCredentials(sessionID, turnSecret)
		config.ICEServers = append(config.ICEServers, webrtc.ICEServer{
			URLs:           turnURLs,
			Username:       username,
			Credential:     credential,
			CredentialType: webrtc.ICECredentialTypePassword,
		})
	}

	pc, err := api.NewPeerConnection(config)
	if err != nil {
		return nil, fmt.Errorf("creating peer connection: %w", err)
	}

	injector, err := input.New()
	if err != nil {
		logger.Printf("input injector unavailable: %v", err)
		injector = input.Noop()
	}

	sess := &DesktopSession{
		sessionID:   sessionID,
		pc:          pc,
		injector:    injector,
		rateLimiter: newRateLimiter(60),
		log:         logger,
		logFile:     logFile,
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
			ft := transfer.New(
				func(text string) error { return dc.SendText(text) },
				func(data []byte) error { return dc.Send(data) },
				"/tmp/pulse-uploads",
				homeDir,
			)
			ft.HandleDataChannel(dc)
		}
	})
}

func (s *DesktopSession) OnICECandidate(fn func(*webrtc.ICECandidate)) {
	s.pc.OnICECandidate(fn)
}

func (s *DesktopSession) addVideoTrack(track webrtc.TrackLocal) error {
	if _, err := s.pc.AddTrack(track); err != nil {
		return err
	}
	s.videoTrack = track
	return nil
}

func (s *DesktopSession) addAudioTrack(track webrtc.TrackLocal) error {
	if _, err := s.pc.AddTrack(track); err != nil {
		return err
	}
	s.audioTrack = track
	return nil
}

func (s *DesktopSession) setScreenSize(w, h int) {
	s.injector.SetScreenSize(w, h)
}

func (s *DesktopSession) OnPeerConnectionClosed(fn func()) {
	once := sync.Once{}
	s.pc.OnConnectionStateChange(func(state webrtc.PeerConnectionState) {
		switch state {
		case webrtc.PeerConnectionStateFailed, webrtc.PeerConnectionStateClosed, webrtc.PeerConnectionStateDisconnected:
			once.Do(fn)
		}
	})
}

func (s *DesktopSession) HandleOffer(sdpOffer string) (string, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	s.log.Printf("HandleOffer: signalingState=%s", s.pc.SignalingState())
	s.log.Printf("Offer SDP m=video section:\n%s", extractMediaSection(sdpOffer, "video"))

	if err := s.pc.SetRemoteDescription(webrtc.SessionDescription{
		Type: webrtc.SDPTypeOffer,
		SDP:  sdpOffer,
	}); err != nil {
		s.log.Printf("SetRemoteDescription error: %v", err)
		return "", fmt.Errorf("setting remote description: %w", err)
	}

	for _, cand := range s.pendingICE {
		s.log.Printf("Adding pending ICE candidate")
		if err := s.pc.AddICECandidate(cand); err != nil {
			s.log.Printf("AddICECandidate (pending) error: %v", err)
		}
	}
	s.pendingICE = nil

	s.log.Printf("Remote description set, signalingState=%s", s.pc.SignalingState())

	answer, err := s.pc.CreateAnswer(nil)
	if err != nil {
		s.log.Printf("CreateAnswer error: %v", err)
		return "", fmt.Errorf("creating answer: %w", err)
	}

	if err := s.pc.SetLocalDescription(answer); err != nil {
		s.log.Printf("SetLocalDescription error: %v", err)
		return "", fmt.Errorf("setting local description: %w", err)
	}

	answerSDP := s.pc.LocalDescription().SDP
	s.log.Printf("Answer ready, signalingState=%s", s.pc.SignalingState())
	s.log.Printf("Answer SDP m=video section:\n%s", extractMediaSection(answerSDP, "video"))

	return answerSDP, nil
}

func (s *DesktopSession) AddICECandidate(candidateJSON string) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	var init webrtc.ICECandidateInit
	if err := json.Unmarshal([]byte(candidateJSON), &init); err != nil {
		return fmt.Errorf("parsing ICE candidate: %w", err)
	}

	if s.pc.RemoteDescription() == nil {
		s.log.Printf("AddICECandidate: remote description not set, queueing candidate")
		s.pendingICE = append(s.pendingICE, init)
		return nil
	}

	s.log.Printf("AddICECandidate: signalingState=%s", s.pc.SignalingState())
	if err := s.pc.AddICECandidate(init); err != nil {
		s.log.Printf("AddICECandidate error: %v", err)
		return err
	}
	return nil
}

func (s *DesktopSession) dispatchInputEvent(ev inputEvent) {
	var err error
	switch ev.Type {
	case "mousemove":
		err = s.injector.MouseMove(ev.X, ev.Y)
	case "mousedown":
		err = s.injector.MouseButton(ev.Button, true)
	case "mouseup":
		err = s.injector.MouseButton(ev.Button, false)
	case "wheel":
		err = s.injector.MouseScroll(ev.DeltaX, ev.DeltaY)
	case "keydown":
		err = s.injector.KeyEvent(ev.KeyCode, true)
	case "keyup":
		err = s.injector.KeyEvent(ev.KeyCode, false)
	}
	if err != nil {
		s.log.Printf("input %s error: %v", ev.Type, err)
	}
}

func (s *DesktopSession) Close() error {
	var err error
	s.closeOnce.Do(func() {
		s.mu.Lock()
		defer s.mu.Unlock()
		s.injector.Close() //nolint:errcheck
		err = s.pc.Close()
		if s.logFile != nil {
			s.logFile.Close()
		}
	})
	return err
}

func getCodecFmtpLine(mimeType string) string {
	if mimeType == webrtc.MimeTypeH264 {
		// H264 Constrained Baseline, Level 4.2 — needed to support 1920×1080@30
		// and typical 1280×800 / 1280×1024 desktops (Level 3.1 caps at 1280×720).
		// profile-level-id=42e02a: 0x42=Baseline, 0xe0=constrained_baseline, 0x2a=Level 4.2
		return "level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e02a"
	}
	return "" // VP9 doesn't need special fmtp line
}

func truncate(s string, maxLen int) string {
	if len(s) > maxLen {
		return s[:maxLen]
	}
	return s
}

// extractMediaSection returns just the m=<kind> ... block from an SDP for logging.
func extractMediaSection(sdp, kind string) string {
	prefix := "m=" + kind + " "
	idx := -1
	for i := 0; i+len(prefix) <= len(sdp); i++ {
		if sdp[i:i+len(prefix)] == prefix && (i == 0 || sdp[i-1] == '\n') {
			idx = i
			break
		}
	}
	if idx < 0 {
		return "(no m=" + kind + " section)"
	}
	end := len(sdp)
	for i := idx + len(prefix); i+2 < len(sdp); i++ {
		if sdp[i] == '\n' && i+2 < len(sdp) && sdp[i+1] == 'm' && sdp[i+2] == '=' {
			end = i
			break
		}
	}
	return sdp[idx:end]
}

func generateTurnCredentials(sessionID, secret string) (username, credential string) {
	expires := time.Now().Add(time.Hour).Unix()
	username = fmt.Sprintf("%d:%s", expires, sessionID)
	mac := hmac.New(sha1.New, []byte(secret))
	mac.Write([]byte(username))
	credential = base64.StdEncoding.EncodeToString(mac.Sum(nil))
	return
}
