package shell

// Session abstracts a pty-backed shell session. Implemented per OS.
type Session interface {
	Write(p []byte) error
	Resize(cols, rows uint32) error
	Close() error
	Out() <-chan []byte
	Done() <-chan struct{}
	ExitCode() int32
}
