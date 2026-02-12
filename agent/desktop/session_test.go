package desktop

import (
	"testing"

	"github.com/pion/webrtc/v4"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestNewSessionCreatesValidPeerConnection(t *testing.T) {
	sess, err := NewSession("test-session-id", nil, "")
	require.NoError(t, err)
	require.NotNil(t, sess)
	require.NotNil(t, sess.pc)
	sess.Close()
}

func TestHandleOfferReturnsAnswer(t *testing.T) {
	sess, err := NewSession("test-session-id", nil, "")
	require.NoError(t, err)
	defer sess.Close()

	videoTrack, err := webrtc.NewTrackLocalStaticSample(
		webrtc.RTPCodecCapability{MimeType: webrtc.MimeTypeVP9, ClockRate: 90000},
		"video", "desktop",
	)
	require.NoError(t, err)
	require.NoError(t, sess.addVideoTrack(videoTrack))

	// simulate browser side: create a receive-only transceiver and generate offer
	browserPC, err := webrtc.NewPeerConnection(webrtc.Configuration{})
	require.NoError(t, err)
	defer browserPC.Close()

	_, err = browserPC.AddTransceiverFromKind(webrtc.RTPCodecTypeVideo, webrtc.RTPTransceiverInit{
		Direction: webrtc.RTPTransceiverDirectionRecvonly,
	})
	require.NoError(t, err)

	offer, err := browserPC.CreateOffer(nil)
	require.NoError(t, err)
	require.NoError(t, browserPC.SetLocalDescription(offer))

	answer, err := sess.HandleOffer(offer.SDP)
	require.NoError(t, err)
	assert.NotEmpty(t, answer)
	assert.Contains(t, answer, "m=video")
}
