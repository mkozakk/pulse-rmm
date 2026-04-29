//go:build linux

package shell

import (
	"bytes"
	"testing"
	"time"
)

func TestSessionEchoOutput(t *testing.T) {
	sess, err := Start("t1", 80, 24)
	if err != nil {
		t.Fatal(err)
	}
	defer sess.Close()

	if err := sess.Write([]byte("echo hi\n")); err != nil {
		t.Fatal(err)
	}

	deadline := time.After(1 * time.Second)
	var buf []byte
	for {
		select {
		case <-deadline:
			t.Fatalf("timeout waiting for output, got: %q", buf)
		case chunk, ok := <-sess.Out():
			if !ok {
				t.Fatalf("output channel closed early, buf: %q", buf)
			}
			buf = append(buf, chunk...)
			if bytes.Contains(buf, []byte("hi")) {
				return
			}
		}
	}
}

func TestSessionExitCode(t *testing.T) {
	sess, err := Start("t2", 80, 24)
	if err != nil {
		t.Fatal(err)
	}

	if err := sess.Write([]byte("exit\n")); err != nil {
		t.Fatal(err)
	}

	select {
	case <-sess.Done():
	case <-time.After(2 * time.Second):
		t.Fatal("session did not finish within 2s")
	}

	if sess.ExitCode() != 0 {
		t.Fatalf("expected exit code 0, got %d", sess.ExitCode())
	}
}

func TestSessionClose(t *testing.T) {
	sess, err := Start("t3", 80, 24)
	if err != nil {
		t.Fatal(err)
	}

	if err := sess.Close(); err != nil {
		t.Fatal(err)
	}

	select {
	case <-sess.Done():
	default:
		t.Fatal("done not closed after Close()")
	}

	if err := sess.Write([]byte("hello")); err == nil {
		t.Fatal("expected error writing to closed session")
	}
}

func TestSessionResize(t *testing.T) {
	sess, err := Start("t4", 80, 24)
	if err != nil {
		t.Fatal(err)
	}
	defer sess.Close()

	if err := sess.Resize(120, 40); err != nil {
		t.Fatalf("resize failed: %v", err)
	}

	if err := sess.Write([]byte("echo ok\n")); err != nil {
		t.Fatalf("write after resize failed: %v", err)
	}
}
