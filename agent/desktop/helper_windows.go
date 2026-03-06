//go:build windows

package desktop

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"net"
	"os"

	"github.com/pion/webrtc/v4"
	"github.com/pulsermm/pulse-rmm/agent/desktop/capture"
)

// RunHelper is the entry point when the agent binary is spawned as
// --desktop-helper <addr>. It runs inside the user's interactive session
// (not Session 0), so gdigrab and SendInput work correctly.
func RunHelper(addr string) {
	conn, err := net.Dial("tcp", addr)
	if err != nil {
		fmt.Fprintf(os.Stderr, "[desktop-helper] connect %s: %v\n", addr, err)
		os.Exit(1)
	}
	defer conn.Close()

	scanner := bufio.NewScanner(conn)

	sendMsg := func(msg pipeMsg) {
		b, _ := json.Marshal(msg)
		fmt.Fprintf(conn, "%s\n", b)
	}

	if !scanner.Scan() {
		fmt.Fprintln(os.Stderr, "[desktop-helper] no config received")
		os.Exit(1)
	}
	var cfg pipeMsg
	if err := json.Unmarshal(scanner.Bytes(), &cfg); err != nil || cfg.Type != "config" {
		fmt.Fprintf(os.Stderr, "[desktop-helper] bad config message\n")
		os.Exit(1)
	}

	sess, err := NewSession(cfg.SessionID, cfg.TurnURLs, cfg.TurnSecret)
	if err != nil {
		sendMsg(pipeMsg{Type: "error", Message: fmt.Sprintf("creating session: %v", err)})
		os.Exit(1)
	}
	defer sess.Close()

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	sess.OnICECandidate(func(c *webrtc.ICECandidate) {
		if c == nil {
			return
		}
		b, _ := json.Marshal(c.ToJSON())
		sendMsg(pipeMsg{Type: "candidate", Candidate: string(b)})
	})

	if err := capture.Start(ctx, capture.Target{Logger: sess.log, LogFile: sess.logFile, AddTrack: sess.addVideoTrack}); err != nil {
		sendMsg(pipeMsg{Type: "error", Message: err.Error()})
		os.Exit(1)
	}

	sess.OnPeerConnectionClosed(func() {
		sendMsg(pipeMsg{Type: "closed"})
		cancel()
	})

	sendMsg(pipeMsg{Type: "ready"})

	for scanner.Scan() {
		var msg pipeMsg
		if err := json.Unmarshal(scanner.Bytes(), &msg); err != nil {
			continue
		}
		switch msg.Type {
		case "offer":
			answer, err := sess.HandleOffer(msg.SDP)
			if err != nil {
				sendMsg(pipeMsg{Type: "error", Message: fmt.Sprintf("handling offer: %v", err)})
				return
			}
			sendMsg(pipeMsg{Type: "answer", SDP: answer})
		case "candidate":
			sess.AddICECandidate(msg.Candidate) //nolint:errcheck
		case "end":
			return
		}
	}
}
