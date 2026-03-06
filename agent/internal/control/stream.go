package control

import (
	"context"
	"fmt"

	pb "github.com/pulsermm/pulse-rmm/agent/gen/pulse/v1"
	"google.golang.org/grpc"
)

// Run opens a long-lived bidirectional stream to the gateway. Commands from the
// gateway are forwarded to inCh; events from outCh are forwarded to the gateway.
// Returns when ctx is cancelled or the stream breaks.
func Run(ctx context.Context, endpointID, gatewayAddr, version string,
	inCh chan<- *pb.GatewayCommand, outCh <-chan *pb.AgentEvent,
	opts ...grpc.DialOption,
) error {
	conn, err := grpc.NewClient(gatewayAddr, opts...)
	if err != nil {
		return fmt.Errorf("dialing gateway: %w", err)
	}
	defer conn.Close()

	stream, err := pb.NewGatewayServiceClient(conn).OpenAgentStream(ctx)
	if err != nil {
		return fmt.Errorf("opening agent stream: %w", err)
	}

	if err := stream.Send(&pb.AgentEvent{
		Payload: &pb.AgentEvent_Hello{
			Hello: &pb.AgentHello{EndpointId: endpointID, AgentVersion: version},
		},
	}); err != nil {
		return fmt.Errorf("sending hello: %w", err)
	}

	recvErr := make(chan error, 1)
	go func() {
		for {
			cmd, err := stream.Recv()
			if err != nil {
				recvErr <- err
				return
			}
			select {
			case inCh <- cmd:
			case <-ctx.Done():
				recvErr <- ctx.Err()
				return
			}
		}
	}()

	for {
		select {
		case <-ctx.Done():
			stream.CloseSend() //nolint:errcheck
			return nil
		case event, ok := <-outCh:
			if !ok {
				stream.CloseSend() //nolint:errcheck
				return nil
			}
			if err := stream.Send(event); err != nil {
				return fmt.Errorf("sending event: %w", err)
			}
		case err := <-recvErr:
			if ctx.Err() != nil {
				return nil
			}
			return fmt.Errorf("receiving command: %w", err)
		}
	}
}
