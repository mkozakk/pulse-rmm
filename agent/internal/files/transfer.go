package files

import (
	"context"
	"fmt"
	"io"
	"os"
	"path/filepath"

	pb "github.com/pulsermm/pulse-rmm/agent/gen/pulse/v1"
)

const chunkSize = 64 * 1024

// SendFile reads path and streams its bytes up to the backend's FileService.Upload.
// Used when the webapp wants to download a file from the agent.
func SendFile(ctx context.Context, client pb.FileServiceClient, transferID, path string) (int64, error) {
	f, err := os.Open(path)
	if err != nil {
		return 0, fmt.Errorf("opening %q: %w", path, err)
	}
	defer f.Close()

	stream, err := client.Upload(ctx)
	if err != nil {
		return 0, fmt.Errorf("opening upload stream: %w", err)
	}

	if err := stream.Send(&pb.FileChunk{TransferId: transferID}); err != nil {
		return 0, fmt.Errorf("sending header: %w", err)
	}

	buf := make([]byte, chunkSize)
	var total int64
	for {
		n, err := f.Read(buf)
		if n > 0 {
			if sendErr := stream.Send(&pb.FileChunk{Data: buf[:n]}); sendErr != nil {
				return total, fmt.Errorf("sending chunk: %w", sendErr)
			}
			total += int64(n)
		}
		if err == io.EOF {
			break
		}
		if err != nil {
			return total, fmt.Errorf("reading file: %w", err)
		}
	}

	ack, err := stream.CloseAndRecv()
	if err != nil {
		return total, fmt.Errorf("closing upload: %w", err)
	}
	return ack.GetBytes(), nil
}

// ReceiveFile pulls bytes from FileService.Download and writes them to destPath.
// Used when the webapp uploads a file to the agent.
func ReceiveFile(ctx context.Context, client pb.FileServiceClient, transferID, destPath string) (int64, error) {
	if err := os.MkdirAll(filepath.Dir(destPath), 0o755); err != nil {
		return 0, fmt.Errorf("creating parent dir: %w", err)
	}
	f, err := os.Create(destPath)
	if err != nil {
		return 0, fmt.Errorf("creating %q: %w", destPath, err)
	}
	defer f.Close()

	stream, err := client.Download(ctx, &pb.FileDownloadRequest{TransferId: transferID})
	if err != nil {
		return 0, fmt.Errorf("opening download stream: %w", err)
	}

	var total int64
	for {
		chunk, err := stream.Recv()
		if err == io.EOF {
			break
		}
		if err != nil {
			return total, fmt.Errorf("receiving chunk: %w", err)
		}
		if len(chunk.Data) > 0 {
			n, werr := f.Write(chunk.Data)
			if werr != nil {
				return total, fmt.Errorf("writing chunk: %w", werr)
			}
			total += int64(n)
		}
	}
	return total, nil
}
