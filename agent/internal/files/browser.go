package files

import (
	"fmt"
	"os"
	"path/filepath"

	pb "github.com/pulsermm/pulse-rmm/agent/gen/pulse/v1"
)

// ListDir returns directory entries at path. When path is empty, returns
// filesystem roots (drive letters on Windows, "/" on Unix).
func ListDir(path string) ([]*pb.DirEntry, error) {
	if path == "" {
		return listRoots()
	}
	abs, err := filepath.Abs(path)
	if err != nil {
		return nil, fmt.Errorf("resolving %q: %w", path, err)
	}
	entries, err := os.ReadDir(abs)
	if err != nil {
		return nil, fmt.Errorf("reading %q: %w", abs, err)
	}
	out := make([]*pb.DirEntry, 0, len(entries))
	for _, e := range entries {
		info, err := e.Info()
		if err != nil {
			continue
		}
		out = append(out, &pb.DirEntry{
			Name:     e.Name(),
			Path:     filepath.Join(abs, e.Name()),
			IsDir:    e.IsDir(),
			Size:     info.Size(),
			Modified: info.ModTime().UnixMilli(),
		})
	}
	return out, nil
}
