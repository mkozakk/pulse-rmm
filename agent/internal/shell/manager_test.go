//go:build linux

package shell

import (
	"bytes"
	"testing"
	"time"

	pb "github.com/pulsermm/pulse-rmm/agent/gen/pulse/v1"
)

func TestManagerOutputRouting(t *testing.T) {
	out := make(chan *pb.AgentEvent, 64)
	mgr := NewManager(out)
	defer mgr.CloseAll()

	if err := mgr.Open("s1", 80, 24); err != nil {
		t.Fatal(err)
	}

	if err := mgr.Input("s1", []byte("echo hello\n")); err != nil {
		t.Fatal(err)
	}

	deadline := time.After(2 * time.Second)
	for {
		select {
		case <-deadline:
			t.Fatal("timeout waiting for output event")
		case ev := <-out:
			o := ev.GetShellOutput()
			if o == nil {
				continue
			}
			if o.SessionId != "s1" {
				t.Fatalf("wrong session id: %s", o.SessionId)
			}
			if bytes.Contains(o.Data, []byte("hello")) {
				return
			}
		}
	}
}

func TestManagerTwoSessions(t *testing.T) {
	out := make(chan *pb.AgentEvent, 64)
	mgr := NewManager(out)
	defer mgr.CloseAll()

	if err := mgr.Open("s1", 80, 24); err != nil {
		t.Fatal(err)
	}
	if err := mgr.Open("s2", 80, 24); err != nil {
		t.Fatal(err)
	}

	mgr.Input("s1", []byte("echo from1\n"))
	mgr.Input("s2", []byte("echo from2\n"))

	got1, got2 := false, false
	deadline := time.After(3 * time.Second)
	for !got1 || !got2 {
		select {
		case <-deadline:
			t.Fatalf("timeout: got1=%v got2=%v", got1, got2)
		case ev := <-out:
			o := ev.GetShellOutput()
			if o == nil {
				continue
			}
			if o.SessionId == "s1" && bytes.Contains(o.Data, []byte("from1")) {
				got1 = true
			}
			if o.SessionId == "s2" && bytes.Contains(o.Data, []byte("from2")) {
				got2 = true
			}
		}
	}

	mgr.Close("s1")

	if err := mgr.Input("s2", []byte("echo still2\n")); err != nil {
		t.Fatalf("s2 write failed after s1 close: %v", err)
	}
}

func TestManagerCloseAll(t *testing.T) {
	out := make(chan *pb.AgentEvent, 64)
	mgr := NewManager(out)

	mgr.Open("s1", 80, 24)
	mgr.Open("s2", 80, 24)
	mgr.CloseAll()

	exited := map[string]bool{}
	deadline := time.After(3 * time.Second)
	for len(exited) < 2 {
		select {
		case <-deadline:
			t.Fatalf("timeout waiting for ShellExited events, got: %v", exited)
		case ev := <-out:
			if e := ev.GetShellExited(); e != nil {
				exited[e.SessionId] = true
			}
		}
	}
}
