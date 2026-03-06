package transfer

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"

	"github.com/pion/webrtc/v4"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func newTestHandler(t *testing.T) (*Handler, *[]string, *[][]byte) {
	t.Helper()
	var texts []string
	var binaries [][]byte
	h := New(
		func(s string) error { texts = append(texts, s); return nil },
		func(b []byte) error { binaries = append(binaries, b); return nil },
		t.TempDir(),
		t.TempDir(),
	)
	return h, &texts, &binaries
}

func msg(isString bool, data string) webrtc.DataChannelMessage {
	return webrtc.DataChannelMessage{IsString: isString, Data: []byte(data)}
}

func TestUploadChunksReassembled(t *testing.T) {
	h, texts, _ := newTestHandler(t)

	h.handleMessage(msg(true, `{"type":"upload_start","name":"hello.txt","size":9}`))
	h.handleMessage(webrtc.DataChannelMessage{IsString: false, Data: []byte("hel")})
	h.handleMessage(webrtc.DataChannelMessage{IsString: false, Data: []byte("lo ")})
	h.handleMessage(webrtc.DataChannelMessage{IsString: false, Data: []byte("wld")})
	h.handleMessage(msg(true, `{"type":"upload_done"}`))

	content, err := os.ReadFile(filepath.Join(h.uploadDir, "hello.txt"))
	require.NoError(t, err)
	assert.Equal(t, "hello wld", string(content))

	require.Len(t, *texts, 1)
	var resp map[string]string
	require.NoError(t, json.Unmarshal([]byte((*texts)[0]), &resp))
	assert.Equal(t, "upload_ok", resp["type"])
}

func TestDownloadChunksFile(t *testing.T) {
	h, texts, binaries := newTestHandler(t)

	content := []byte("chunk one chunk two chunk three")
	err := os.WriteFile(filepath.Join(h.homeDir, "report.txt"), content, 0600)
	require.NoError(t, err)

	h.handleMessage(msg(true, `{"type":"download_request","path":"report.txt"}`))

	require.NotEmpty(t, *texts)
	var start map[string]any
	require.NoError(t, json.Unmarshal([]byte((*texts)[0]), &start))
	assert.Equal(t, "download_start", start["type"])

	var got []byte
	for _, chunk := range *binaries {
		got = append(got, chunk...)
	}
	assert.Equal(t, content, got)

	last := (*texts)[len(*texts)-1]
	var done map[string]string
	require.NoError(t, json.Unmarshal([]byte(last), &done))
	assert.Equal(t, "download_done", done["type"])
}

func TestUploadCreatesDirectory(t *testing.T) {
	uploadDir := filepath.Join(t.TempDir(), "uploads", "nested")
	h := New(
		func(s string) error { return nil },
		func(b []byte) error { return nil },
		uploadDir,
		t.TempDir(),
	)

	h.handleMessage(msg(true, `{"type":"upload_start","name":"f.txt","size":1}`))
	h.handleMessage(webrtc.DataChannelMessage{IsString: false, Data: []byte("x")})
	h.handleMessage(msg(true, `{"type":"upload_done"}`))

	_, err := os.Stat(uploadDir)
	require.NoError(t, err, "upload directory should have been created")
}

func TestDownloadPathTraversalRejected(t *testing.T) {
	h, texts, _ := newTestHandler(t)

	h.handleMessage(msg(true, `{"type":"download_request","path":"../../etc/passwd"}`))

	require.Len(t, *texts, 1)
	var resp map[string]string
	require.NoError(t, json.Unmarshal([]byte((*texts)[0]), &resp))
	assert.Equal(t, "error", resp["type"])
}
