package desktop

import (
	"crypto/hmac"
	"crypto/sha1"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/pion/webrtc/v4"
)

var ErrWaylandNotSupported = errors.New("wayland is not supported")

type DesktopSession struct {
	sessionID  string
	pc         *webrtc.PeerConnection
	videoTrack webrtc.TrackLocal
}

func NewSession(sessionID string, turnURLs []string, turnSecret string) (*DesktopSession, error) {
	m := &webrtc.MediaEngine{}
	if err := m.RegisterCodec(webrtc.RTPCodecParameters{
		RTPCodecCapability: webrtc.RTPCodecCapability{
			MimeType:  webrtc.MimeTypeVP9,
			ClockRate: 90000,
		},
		PayloadType: 98,
	}, webrtc.RTPCodecTypeVideo); err != nil {
		return nil, fmt.Errorf("registering VP9 codec: %w", err)
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

	return &DesktopSession{sessionID: sessionID, pc: pc}, nil
}

func (s *DesktopSession) addVideoTrack(track webrtc.TrackLocal) error {
	if _, err := s.pc.AddTrack(track); err != nil {
		return err
	}
	s.videoTrack = track
	return nil
}

func (s *DesktopSession) HandleOffer(sdpOffer string) (string, error) {
	if err := s.pc.SetRemoteDescription(webrtc.SessionDescription{
		Type: webrtc.SDPTypeOffer,
		SDP:  sdpOffer,
	}); err != nil {
		return "", fmt.Errorf("setting remote description: %w", err)
	}

	answer, err := s.pc.CreateAnswer(nil)
	if err != nil {
		return "", fmt.Errorf("creating answer: %w", err)
	}

	gatherComplete := webrtc.GatheringCompletePromise(s.pc)
	if err := s.pc.SetLocalDescription(answer); err != nil {
		return "", fmt.Errorf("setting local description: %w", err)
	}
	<-gatherComplete

	return s.pc.LocalDescription().SDP, nil
}

func (s *DesktopSession) AddICECandidate(candidateJSON string) error {
	var init webrtc.ICECandidateInit
	if err := json.Unmarshal([]byte(candidateJSON), &init); err != nil {
		return fmt.Errorf("parsing ICE candidate: %w", err)
	}
	return s.pc.AddICECandidate(init)
}

func (s *DesktopSession) Close() error {
	return s.pc.Close()
}

func generateTurnCredentials(sessionID, secret string) (username, credential string) {
	expires := time.Now().Add(time.Hour).Unix()
	username = fmt.Sprintf("%d:%s", expires, sessionID)
	mac := hmac.New(sha1.New, []byte(secret))
	mac.Write([]byte(username))
	credential = base64.StdEncoding.EncodeToString(mac.Sum(nil))
	return
}
