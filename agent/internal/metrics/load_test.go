package metrics

import (
	"context"
	"crypto/rand"
	"fmt"
	"os"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

func randomUUID() string {
	var b [16]byte
	rand.Read(b)
	b[6] = (b[6] & 0x0f) | 0x40
	b[8] = (b[8] & 0x3f) | 0x80
	return fmt.Sprintf("%x-%x-%x-%x-%x", b[0:4], b[4:6], b[6:8], b[8:10], b[10:])
}

func TestLoad200Agents(t *testing.T) {
	addr := os.Getenv("PULSE_METRIC_SERVER")
	if addr == "" {
		t.Skip("set PULSE_METRIC_SERVER=host:port to run load test")
	}

	const n = 200
	var wg sync.WaitGroup
	var errCount int64

	for i := 0; i < n; i++ {
		wg.Add(1)
		endpointID := randomUUID()
		go func() {
			defer wg.Done()

			client, err := NewClient(addr)
			if err != nil {
				atomic.AddInt64(&errCount, 1)
				return
			}
			defer client.Close()

			samples := []Sample{
				{Type: "cpu", Value: 50.0, CollectedAt: time.Now()},
				{Type: "ram", Value: 60.0, CollectedAt: time.Now()},
			}
			if err := client.PushMetrics(context.Background(), endpointID, samples); err != nil {
				atomic.AddInt64(&errCount, 1)
			}
		}()
	}

	wg.Wait()

	if errCount > 0 {
		t.Errorf("%d out of %d agents failed to push metrics", errCount, n)
	}
}
