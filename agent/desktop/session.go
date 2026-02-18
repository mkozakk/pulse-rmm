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
)

var ErrWaylandNotSupported = errors.New("wayland is not supported")

type DesktopSession struct {
	sessionID   string
	pc          *webrtc.PeerConnection
	videoTrack  webrtc.TrackLocal
	injector    InputInjector
	rateLimiter *rateLimiter
	log         *log.Logger
	logFile     *os.File
}

func newSessionLogger(sessionID string) (*log.Logger, *os.File) {
	logPath := filepath.Join(os.TempDir(), "pulse-desktop-"+sessionID+".log")
	f, err := os.Create(logPath)
	if err != nil {
		fmt.Fprintf(os.Stderr, "[desktop] could not create log file %s: %v\n", logPath, err)
		return log.New(os.Stdout, "[desktop] ", log.LstdFlags), nil
	}
	fmt.Printf("[desktop] session log: %s\n", logPath)
	return log.New(io.MultiWriter(os.Stdout, f), "[desktop] ", log.LstdFlags), f
}

func NewSession(sessionID string, turnURLs []string, turnSecret string) (*DesktopSession, error) {
	m := &webrtc.MediaEngine{}

	logger, logFile := newSessionLogger(sessionID)

	if runtime.GOOS == "windows" {
		if err := m.RegisterCodec(webrtc.RTPCodecParameters{
			RTPCodecCapability: webrtc.RTPCodecCapability{
				MimeType:    webrtc.MimeTypeH264,
				ClockRate:   90000,
				SDPFmtpLine: "level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e01f",
			},
			PayloadType: 102,
		}, webrtc.RTPCodecTypeVideo); err != nil {
			return nil, fmt.Errorf("registering H264 codec: %w", err)
		}
		logger.Println("Registering H264 codec for Windows")
	} else {
		// Linux: H264 primary (hardware or libx264), VP9 as last resort fallback
		if err := m.RegisterCodec(webrtc.RTPCodecParameters{
			RTPCodecCapability: webrtc.RTPCodecCapability{
				MimeType:    webrtc.MimeTypeH264,
				ClockRate:   90000,
				SDPFmtpLine: "level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e01f",
			},
			PayloadType: 102,
		}, webrtc.RTPCodecTypeVideo); err != nil {
			return nil, fmt.Errorf("registering H264 codec: %w", err)
		}
		if err := m.RegisterCodec(webrtc.RTPCodecParameters{
			RTPCodecCapability: webrtc.RTPCodecCapability{
				MimeType:  webrtc.MimeTypeVP9,
				ClockRate: 90000,
			},
			PayloadType: 98,
		}, webrtc.RTPCodecTypeVideo); err != nil {
			return nil, fmt.Errorf("registering VP9 codec: %w", err)
		}
		logger.Println("Registering H264 (primary) and VP9 (fallback) codecs for Linux")
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
		logger.Printf("input injector unavailable: %v", err)
		injector = &noopInjector{}
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
	s.log.Printf("HandleOffer: signalingState=%s", s.pc.SignalingState())
	s.log.Printf("Offer SDP (first 200 chars): %s...", truncate(sdpOffer, 200))

	if err := s.pc.SetRemoteDescription(webrtc.SessionDescription{
		Type: webrtc.SDPTypeOffer,
		SDP:  sdpOffer,
	}); err != nil {
		s.log.Printf("SetRemoteDescription error: %v", err)
		return "", fmt.Errorf("setting remote description: %w", err)
	}

	s.log.Printf("Remote description set, signalingState=%s", s.pc.SignalingState())

	answer, err := s.pc.CreateAnswer(nil)
	if err != nil {
		s.log.Printf("CreateAnswer error: %v", err)
		return "", fmt.Errorf("creating answer: %w", err)
	}

	s.log.Printf("Answer created, waiting for ICE gathering...")

	gatherComplete := webrtc.GatheringCompletePromise(s.pc)
	if err := s.pc.SetLocalDescription(answer); err != nil {
		s.log.Printf("SetLocalDescription error: %v", err)
		return "", fmt.Errorf("setting local description: %w", err)
	}
	<-gatherComplete

	answerSDP := s.pc.LocalDescription().SDP
	s.log.Printf("Answer ready, signalingState=%s", s.pc.SignalingState())
	s.log.Printf("Answer SDP (first 200 chars): %s...", truncate(answerSDP, 200))

	return answerSDP, nil
}

func (s *DesktopSession) AddICECandidate(candidateJSON string) error {
	var init webrtc.ICECandidateInit
	if err := json.Unmarshal([]byte(candidateJSON), &init); err != nil {
		return fmt.Errorf("parsing ICE candidate: %w", err)
	}

	if s.pc.RemoteDescription() == nil {
		s.log.Printf("AddICECandidate: remote description not set, candidate dropped")
		return fmt.Errorf("remote description not set")
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
	s.injector.Close() //nolint:errcheck
	err := s.pc.Close()
	if s.logFile != nil {
		s.logFile.Close()
	}
	return err
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
