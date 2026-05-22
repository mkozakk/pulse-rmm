package files_test

import (
	"bytes"
	"context"
	"crypto/rand"
	"io"
	"net"
	"os"
	"path/filepath"
	"sync"
	"testing"

	pb "github.com/pulsermm/pulse-rmm/agent/gen/pulse/v1"
	"github.com/pulsermm/pulse-rmm/agent/internal/files"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/test/bufconn"
)

// fakeFileServer mimics the gateway's FileService side:
// - Upload collects all chunks into a buffer.
// - Download streams a pre-set buffer back, chunked.
type fakeFileServer struct {
	pb.UnimplementedFileServiceServer
	mu        sync.Mutex
	uploaded  bytes.Buffer
	downloadB []byte
}

func (s *fakeFileServer) Upload(stream pb.FileService_UploadServer) error {
	var total int64
	for {
		chunk, err := stream.Recv()
		if err == io.EOF {
			return stream.SendAndClose(&pb.FileTransferAck{Bytes: total})
		}
		if err != nil {
			return err
		}
		if len(chunk.Data) > 0 {
			s.mu.Lock()
			s.uploaded.Write(chunk.Data)
			s.mu.Unlock()
			total += int64(len(chunk.Data))
		}
	}
}

func (s *fakeFileServer) Download(_ *pb.FileDownloadRequest, stream pb.FileService_DownloadServer) error {
	const cs = 64 * 1024
	for off := 0; off < len(s.downloadB); off += cs {
		end := off + cs
		if end > len(s.downloadB) {
			end = len(s.downloadB)
		}
		if err := stream.Send(&pb.FileChunk{Data: s.downloadB[off:end]}); err != nil {
			return err
		}
	}
	return nil
}

func newFileClient(t *testing.T, srv *fakeFileServer) pb.FileServiceClient {
	t.Helper()
	lis := bufconn.Listen(1024 * 1024)
	gs := grpc.NewServer()
	pb.RegisterFileServiceServer(gs, srv)
	go gs.Serve(lis) //nolint:errcheck
	t.Cleanup(func() { gs.Stop(); lis.Close() })

	conn, err := grpc.NewClient("passthrough://bufnet",
		grpc.WithContextDialer(func(ctx context.Context, _ string) (net.Conn, error) {
			return lis.DialContext(ctx)
		}),
		grpc.WithTransportCredentials(insecure.NewCredentials()),
	)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { conn.Close() })
	return pb.NewFileServiceClient(conn)
}

func TestSendFileChunked(t *testing.T) {
	// 150KB ensures chunkSize (64KB) boundary is crossed multiple times.
	payload := make([]byte, 150*1024)
	if _, err := rand.Read(payload); err != nil {
		t.Fatal(err)
	}

	src := filepath.Join(t.TempDir(), "src.bin")
	if err := os.WriteFile(src, payload, 0o644); err != nil {
		t.Fatal(err)
	}

	srv := &fakeFileServer{}
	client := newFileClient(t, srv)

	n, err := files.SendFile(context.Background(), client, "tid-1", src)
	if err != nil {
		t.Fatalf("SendFile: %v", err)
	}
	if n != int64(len(payload)) {
		t.Errorf("ack bytes = %d, want %d", n, len(payload))
	}
	if !bytes.Equal(srv.uploaded.Bytes(), payload) {
		t.Errorf("uploaded bytes do not match source")
	}
}

func TestReceiveFile(t *testing.T) {
	payload := make([]byte, 80*1024)
	if _, err := rand.Read(payload); err != nil {
		t.Fatal(err)
	}

	srv := &fakeFileServer{downloadB: payload}
	client := newFileClient(t, srv)

	dst := filepath.Join(t.TempDir(), "out", "got.bin")
	n, err := files.ReceiveFile(context.Background(), client, "tid-2", dst)
	if err != nil {
		t.Fatalf("ReceiveFile: %v", err)
	}
	if n != int64(len(payload)) {
		t.Errorf("written bytes = %d, want %d", n, len(payload))
	}
	got, err := os.ReadFile(dst)
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(got, payload) {
		t.Errorf("written file content mismatch")
	}
}
