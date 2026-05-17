package transfer

import (
	"encoding/json"
	"io"
	"os"
	"path/filepath"
	"strings"

	"github.com/pion/webrtc/v4"
)

const uploadChunkSize = 64 * 1024

type Handler struct {
	send       func(string) error
	sendBinary func([]byte) error
	uploadDir  string
	homeDir    string

	uploadFile *os.File
}

func New(
	send func(string) error,
	sendBinary func([]byte) error,
	uploadDir string,
	homeDir string,
) *Handler {
	return &Handler{
		send:       send,
		sendBinary: sendBinary,
		uploadDir:  uploadDir,
		homeDir:    homeDir,
	}
}

func (h *Handler) HandleDataChannel(dc *webrtc.DataChannel) {
	dc.OnMessage(h.handleMessage)
}

func (h *Handler) handleMessage(msg webrtc.DataChannelMessage) {
	if msg.IsString {
		h.handleControl(msg.Data)
	} else {
		h.handleBinary(msg.Data)
	}
}

type ftMsg struct {
	Type string `json:"type"`
	Name string `json:"name,omitempty"`
	Size int64  `json:"size,omitempty"`
	Path string `json:"path,omitempty"`
}

func (h *Handler) handleControl(data []byte) {
	var m ftMsg
	if err := json.Unmarshal(data, &m); err != nil {
		h.sendErr("invalid message")
		return
	}
	switch m.Type {
	case "upload_start":
		h.handleUploadStart(m)
	case "upload_done":
		h.handleUploadDone()
	case "download_request":
		h.handleDownloadRequest(m)
	}
}

func (h *Handler) handleUploadStart(m ftMsg) {
	if err := os.MkdirAll(h.uploadDir, 0755); err != nil {
		h.sendErr("cannot create upload directory")
		return
	}
	name := filepath.Base(m.Name)
	f, err := os.Create(filepath.Join(h.uploadDir, name))
	if err != nil {
		h.sendErr("cannot create file")
		return
	}
	h.uploadFile = f
}

func (h *Handler) handleBinary(data []byte) {
	if h.uploadFile == nil {
		return
	}
	h.uploadFile.Write(data) //nolint:errcheck
}

func (h *Handler) handleUploadDone() {
	if h.uploadFile == nil {
		return
	}
	path := h.uploadFile.Name()
	h.uploadFile.Close()
	h.uploadFile = nil

	resp, _ := json.Marshal(map[string]string{"type": "upload_ok", "path": path})
	h.send(string(resp)) //nolint:errcheck
}

func (h *Handler) handleDownloadRequest(m ftMsg) {
	if filepath.IsAbs(m.Path) {
		h.sendErr("absolute paths not allowed")
		return
	}
	abs := filepath.Join(h.homeDir, m.Path)
	rel, err := filepath.Rel(h.homeDir, abs)
	if err != nil || strings.HasPrefix(rel, "..") {
		h.sendErr("path outside home directory")
		return
	}

	f, err := os.Open(abs)
	if err != nil {
		h.sendErr("file not found")
		return
	}
	defer f.Close()

	info, err := f.Stat()
	if err != nil {
		h.sendErr("cannot stat file")
		return
	}

	start, _ := json.Marshal(map[string]any{
		"type": "download_start",
		"name": filepath.Base(m.Path),
		"size": info.Size(),
	})
	h.send(string(start)) //nolint:errcheck

	buf := make([]byte, uploadChunkSize)
	for {
		n, err := f.Read(buf)
		if n > 0 {
			chunk := make([]byte, n)
			copy(chunk, buf[:n])
			h.sendBinary(chunk) //nolint:errcheck
		}
		if err == io.EOF {
			break
		}
		if err != nil {
			break
		}
	}

	done, _ := json.Marshal(map[string]string{"type": "download_done"})
	h.send(string(done)) //nolint:errcheck
}

func (h *Handler) sendErr(msg string) {
	resp, _ := json.Marshal(map[string]string{"type": "error", "message": msg})
	h.send(string(resp)) //nolint:errcheck
}
