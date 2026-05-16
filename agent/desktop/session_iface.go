package desktop

// desktopSession is what handler.go talks to — either a local DesktopSession
// (Linux) or a helperProxy that forwards signaling to a helper process spawned
// inside the user's interactive session (Windows, to escape Session 0 isolation).
type desktopSession interface {
	HandleOffer(sdp string) (string, error)
	AddICECandidate(candidateJSON string) error
	OnPeerConnectionClosed(fn func())
	Close() error
}
