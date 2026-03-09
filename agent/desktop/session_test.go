package desktop

import (
	"testing"

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
	t.Skip("WebRTC codec negotiation flaky; depends on system codecs")
}
