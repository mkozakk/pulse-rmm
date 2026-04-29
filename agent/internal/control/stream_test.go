package control_test

import (
	"context"
	"net"
	"testing"
	"time"

	"github.com/pulsermm/pulse-rmm/agent/internal/control"
	pb "github.com/pulsermm/pulse-rmm/agent/gen/pulse/v1"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/test/bufconn"
)

const bufSize = 1024 * 1024

type fakeGatewayServer struct {
	pb.UnimplementedGatewayServiceServer
	received chan *pb.AgentEvent
	toSend   chan *pb.GatewayCommand
}

func (f *fakeGatewayServer) OpenAgentStream(stream pb.GatewayService_OpenAgentStreamServer) error {
	go func() {
		for cmd := range f.toSend {
			stream.Send(cmd) //nolint:errcheck
		}
	}()
	for {
		event, err := stream.Recv()
		if err != nil {
			return err
		}
		f.received <- event
	}
}

func startFakeServer(t *testing.T) (*fakeGatewayServer, func(context.Context, string) (net.Conn, error)) {
	t.Helper()
	lis := bufconn.Listen(bufSize)
	svc := &fakeGatewayServer{
		received: make(chan *pb.AgentEvent, 10),
		toSend:   make(chan *pb.GatewayCommand, 10),
	}
	srv := grpc.NewServer()
	pb.RegisterGatewayServiceServer(srv, svc)
	go srv.Serve(lis) //nolint:errcheck
	t.Cleanup(func() {
		close(svc.toSend)
		srv.Stop()
		lis.Close()
	})
	dial := func(ctx context.Context, _ string) (net.Conn, error) {
		return lis.DialContext(ctx)
	}
	return svc, dial
}

func runStream(ctx context.Context, t *testing.T, dial func(context.Context, string) (net.Conn, error)) (chan<- *pb.AgentEvent, <-chan *pb.GatewayCommand, <-chan error) {
	t.Helper()
	outCh := make(chan *pb.AgentEvent, 10)
	inCh := make(chan *pb.GatewayCommand, 10)
	done := make(chan error, 1)
	go func() {
		done <- control.Run(ctx, "test-endpoint-id", "passthrough:///bufnet", "0.5.0", inCh, outCh,
			grpc.WithContextDialer(dial),
			grpc.WithTransportCredentials(insecure.NewCredentials()),
		)
	}()
	return outCh, inCh, done
}

func TestSendsHelloFirst(t *testing.T) {
	svc, dial := startFakeServer(t)
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	runStream(ctx, t, dial)

	select {
	case event := <-svc.received:
		hello := event.GetHello()
		if hello == nil {
			t.Fatalf("first message was not AgentHello: %T", event.Payload)
		}
		if hello.EndpointId != "test-endpoint-id" {
			t.Errorf("expected endpoint_id=test-endpoint-id, got %q", hello.EndpointId)
		}
		if hello.AgentVersion != "0.5.0" {
			t.Errorf("expected agent_version=0.5.0, got %q", hello.AgentVersion)
		}
	case <-ctx.Done():
		t.Fatal("timed out waiting for AgentHello")
	}
}

func TestCommandReachesInCh(t *testing.T) {
	svc, dial := startFakeServer(t)
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	_, inCh, _ := runStream(ctx, t, dial)

	// wait for hello so the stream is established
	select {
	case <-svc.received:
	case <-ctx.Done():
		t.Fatal("timed out waiting for hello")
	}

	svc.toSend <- &pb.GatewayCommand{
		Payload: &pb.GatewayCommand_OpenShell{
			OpenShell: &pb.OpenShell{SessionId: "sess-1", Cols: 80, Rows: 24},
		},
	}

	select {
	case cmd := <-inCh:
		shell := cmd.GetOpenShell()
		if shell == nil {
			t.Fatalf("expected OpenShell, got %T", cmd.Payload)
		}
		if shell.SessionId != "sess-1" {
			t.Errorf("expected session_id=sess-1, got %q", shell.SessionId)
		}
	case <-ctx.Done():
		t.Fatal("timed out waiting for command on inCh")
	}
}

func TestExitsOnContextCancel(t *testing.T) {
	_, dial := startFakeServer(t)
	ctx, cancel := context.WithCancel(context.Background())

	_, _, done := runStream(ctx, t, dial)

	cancel()

	select {
	case <-done:
	case <-time.After(500 * time.Millisecond):
		t.Fatal("Run did not exit within 500ms after context cancel")
	}
}
