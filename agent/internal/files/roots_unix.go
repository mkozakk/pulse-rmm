//go:build !windows

package files

import (
	pb "github.com/pulsermm/pulse-rmm/agent/gen/pulse/v1"
)

func listRoots() ([]*pb.DirEntry, error) {
	return []*pb.DirEntry{{Name: "/", Path: "/", IsDir: true}}, nil
}
