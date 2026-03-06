//go:build windows

package desktop

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"net"
	"os"
	"sync"
	"time"
	"unsafe"

	pb "github.com/pulsermm/pulse-rmm/agent/gen/pulse/v1"
	"golang.org/x/sys/windows"
)

var (
	kernel32                         = windows.NewLazySystemDLL("kernel32.dll")
	procWTSGetActiveConsoleSessionId = kernel32.NewProc("WTSGetActiveConsoleSessionId")

	wtsapi32              = windows.NewLazySystemDLL("wtsapi32.dll")
	procWTSQueryUserToken = wtsapi32.NewProc("WTSQueryUserToken")

	userenv             = windows.NewLazySystemDLL("userenv.dll")
	procCreateEnvBlock  = userenv.NewProc("CreateEnvironmentBlock")
	procDestroyEnvBlock = userenv.NewProc("DestroyEnvironmentBlock")
)

type pipeMsg struct {
	Type       string   `json:"type"`
	SDP        string   `json:"sdp,omitempty"`
	Candidate  string   `json:"candidate,omitempty"`
	TurnURLs   []string `json:"turn_urls,omitempty"`
	TurnSecret string   `json:"turn_secret,omitempty"`
	SessionID  string   `json:"session_id,omitempty"`
	Message    string   `json:"message,omitempty"`
}

type helperProxy struct {
	conn      net.Conn
	sessionID string
	send      func(*pb.AgentEvent)
	answerCh  chan string
	closedCh  chan struct{}
	mu        sync.Mutex
	onClosed  func()
}

func (h *helperProxy) readLoop(scanner *bufio.Scanner) {
	defer close(h.closedCh)
	for scanner.Scan() {
		var msg pipeMsg
		if err := json.Unmarshal(scanner.Bytes(), &msg); err != nil {
			continue
		}
		switch msg.Type {
		case "answer":
			select {
			case h.answerCh <- msg.SDP:
			default:
			}
		case "candidate":
			h.send(&pb.AgentEvent{
				Payload: &pb.AgentEvent_DesktopSignal{
					DesktopSignal: &pb.DesktopSignalMessage{
						SessionId: h.sessionID,
						Type:      "candidate",
						Payload:   msg.Candidate,
					},
				},
			})
		case "closed":
			h.mu.Lock()
			fn := h.onClosed
			h.mu.Unlock()
			if fn != nil {
				fn()
			}
			return
		case "error":
			h.send(&pb.AgentEvent{
				Payload: &pb.AgentEvent_SessionReady{
					SessionReady: &pb.SessionReadyEvent{
						SessionId: h.sessionID,
						Error:     msg.Message,
					},
				},
			})
			return
		}
	}
	// pipe closed unexpectedly
	h.mu.Lock()
	fn := h.onClosed
	h.mu.Unlock()
	if fn != nil {
		fn()
	}
}

func (h *helperProxy) HandleOffer(sdp string) (string, error) {
	b, _ := json.Marshal(pipeMsg{Type: "offer", SDP: sdp})
	if _, err := fmt.Fprintf(h.conn, "%s\n", b); err != nil {
		return "", fmt.Errorf("writing offer to helper: %w", err)
	}
	select {
	case answer := <-h.answerCh:
		return answer, nil
	case <-h.closedCh:
		return "", fmt.Errorf("helper process closed before answering")
	case <-time.After(30 * time.Second):
		return "", fmt.Errorf("timeout waiting for SDP answer from helper")
	}
}

func (h *helperProxy) AddICECandidate(candidateJSON string) error {
	b, _ := json.Marshal(pipeMsg{Type: "candidate", Candidate: candidateJSON})
	_, err := fmt.Fprintf(h.conn, "%s\n", b)
	return err
}

func (h *helperProxy) OnPeerConnectionClosed(fn func()) {
	h.mu.Lock()
	h.onClosed = fn
	h.mu.Unlock()
}

func (h *helperProxy) Close() error {
	b, _ := json.Marshal(pipeMsg{Type: "end"})
	fmt.Fprintf(h.conn, "%s\n", b)
	return h.conn.Close()
}

func startPlatformSession(sessionID string, turnURLs []string, turnSecret string, ctx context.Context, send func(*pb.AgentEvent)) (desktopSession, error) {
	// Find the active interactive session (the logged-in user's desktop).
	r, _, _ := procWTSGetActiveConsoleSessionId.Call()
	consoleSession := uint32(r)
	if consoleSession == 0xFFFFFFFF {
		return nil, ErrNoActiveUserSession
	}

	// Get the user's primary token so we can spawn a process in their session.
	var impToken windows.Token
	r2, _, err := procWTSQueryUserToken.Call(uintptr(consoleSession), uintptr(unsafe.Pointer(&impToken)))
	if r2 == 0 {
		return nil, ErrNoActiveUserSession
	}
	defer impToken.Close()

	var primaryToken windows.Token
	if err = windows.DuplicateTokenEx(impToken, windows.TOKEN_ALL_ACCESS, nil, windows.SecurityImpersonation, windows.TokenPrimary, &primaryToken); err != nil {
		return nil, fmt.Errorf("duplicating user token: %w", err)
	}
	defer primaryToken.Close()

	// Build the user's environment block so the helper inherits the right PATH.
	var envBlock *uint16
	creationFlags := uint32(windows.CREATE_UNICODE_ENVIRONMENT | windows.CREATE_NO_WINDOW)
	r3, _, _ := procCreateEnvBlock.Call(uintptr(unsafe.Pointer(&envBlock)), uintptr(primaryToken), 0)
	if r3 == 0 {
		creationFlags = windows.CREATE_NO_WINDOW
	} else {
		defer procDestroyEnvBlock.Call(uintptr(unsafe.Pointer(envBlock)))
	}

	// Listen on loopback; helper will connect back once spawned.
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return nil, fmt.Errorf("listening for helper: %w", err)
	}

	exePath, err := os.Executable()
	if err != nil {
		ln.Close()
		return nil, fmt.Errorf("resolving executable path: %w", err)
	}

	addr := ln.Addr().String()
	cmdLine, err := windows.UTF16PtrFromString(fmt.Sprintf(`"%s" --desktop-helper %s`, exePath, addr))
	if err != nil {
		ln.Close()
		return nil, fmt.Errorf("building command line: %w", err)
	}

	si := &windows.StartupInfo{Cb: uint32(unsafe.Sizeof(windows.StartupInfo{}))}
	pi := &windows.ProcessInformation{}

	if err = windows.CreateProcessAsUser(primaryToken, nil, cmdLine, nil, nil, false, creationFlags, envBlock, nil, si, pi); err != nil {
		ln.Close()
		return nil, fmt.Errorf("spawning desktop helper: %w", err)
	}
	windows.CloseHandle(pi.Process)
	windows.CloseHandle(pi.Thread)

	// Accept the helper's connection (give it 10s to start up).
	ln.(*net.TCPListener).SetDeadline(time.Now().Add(10 * time.Second))
	conn, err := ln.Accept()
	ln.Close()
	if err != nil {
		return nil, fmt.Errorf("helper did not connect in time: %w", err)
	}
	conn.(*net.TCPConn).SetDeadline(time.Time{})

	// Send session config to helper.
	cfg, _ := json.Marshal(pipeMsg{
		Type:       "config",
		SessionID:  sessionID,
		TurnURLs:   turnURLs,
		TurnSecret: turnSecret,
	})
	fmt.Fprintf(conn, "%s\n", cfg)

	// Wait for helper to signal ready (or error) before we tell the browser we're ready.
	scanner := bufio.NewScanner(conn)
	readyCh := make(chan string, 1)
	go func() {
		if scanner.Scan() {
			var msg pipeMsg
			if err := json.Unmarshal(scanner.Bytes(), &msg); err == nil {
				switch msg.Type {
				case "ready":
					readyCh <- ""
				case "error":
					readyCh <- msg.Message
				default:
					readyCh <- "unexpected message from helper: " + msg.Type
				}
				return
			}
		}
		readyCh <- "helper disconnected before sending ready"
	}()

	select {
	case errMsg := <-readyCh:
		if errMsg != "" {
			conn.Close()
			return nil, fmt.Errorf("%s", errMsg)
		}
	case <-time.After(15 * time.Second):
		conn.Close()
		return nil, fmt.Errorf("timeout waiting for desktop helper to become ready")
	case <-ctx.Done():
		conn.Close()
		return nil, ctx.Err()
	}

	proxy := &helperProxy{
		conn:      conn,
		sessionID: sessionID,
		send:      send,
		answerCh:  make(chan string, 1),
		closedCh:  make(chan struct{}),
	}
	go proxy.readLoop(scanner)

	return proxy, nil
}
