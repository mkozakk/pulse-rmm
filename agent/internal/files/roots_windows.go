//go:build windows

package files

import (
	"fmt"
	"os"

	pb "github.com/pulsermm/pulse-rmm/agent/gen/pulse/v1"
)

func listRoots() ([]*pb.DirEntry, error) {
	out := []*pb.DirEntry{}
	for c := 'A'; c <= 'Z'; c++ {
		p := fmt.Sprintf("%c:\\", c)
		if _, err := os.Stat(p); err == nil {
			out = append(out, &pb.DirEntry{
				Name:  fmt.Sprintf("%c:", c),
				Path:  p,
				IsDir: true,
			})
		}
	}
	return out, nil
}
