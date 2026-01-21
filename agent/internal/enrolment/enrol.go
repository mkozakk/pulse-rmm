package enrolment

import (
	"context"
	"crypto/ed25519"
	"fmt"
	"os"
	"runtime"

	pb "github.com/pulsermm/pulse-rmm/agent/gen/pulse/v1"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
)

func Enrol(ctx context.Context, token string, grpcAddr string, privKey ed25519.PrivateKey) (string, error) {
	pubKey := privKey.Public().(ed25519.PublicKey)

	conn, err := grpc.NewClient(grpcAddr, grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		return "", fmt.Errorf("dialing grpc: %w", err)
	}
	defer conn.Close()

	client := pb.NewAgentServiceClient(conn)

	hostname, err := os.Hostname()
	if err != nil {
		return "", fmt.Errorf("getting hostname: %w", err)
	}

	req := &pb.EnrolRequest{
		Token:     token,
		PublicKey: pubKey,
		Hostname:  hostname,
		Os:        runtime.GOOS,
		Arch:      runtime.GOARCH,
	}

	resp, err := client.Enrol(ctx, req)
	if err != nil {
		return "", fmt.Errorf("enrol rpc: %w", err)
	}

	return resp.EndpointId, nil
}
