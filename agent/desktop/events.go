package desktop

import (
	"encoding/json"
	"fmt"
	"sync"
	"time"
)

type inputEvent struct {
	Type    string `json:"type"`
	X       int    `json:"x"`
	Y       int    `json:"y"`
	Button  int    `json:"button"`
	KeyCode int    `json:"keyCode"`
	DeltaX  int    `json:"deltaX"`
	DeltaY  int    `json:"deltaY"`
}

func parseInputEvent(data []byte) (inputEvent, error) {
	var ev inputEvent
	if err := json.Unmarshal(data, &ev); err != nil {
		return ev, fmt.Errorf("parsing input event: %w", err)
	}
	return ev, nil
}

type rateLimiter struct {
	mu          sync.Mutex
	count       int
	windowStart time.Time
	maxPerSec   int
}

func newRateLimiter(maxPerSec int) *rateLimiter {
	return &rateLimiter{maxPerSec: maxPerSec, windowStart: time.Now()}
}

func (r *rateLimiter) allow() bool {
	r.mu.Lock()
	defer r.mu.Unlock()
	now := time.Now()
	if now.Sub(r.windowStart) >= time.Second {
		r.count = 0
		r.windowStart = now
	}
	if r.count >= r.maxPerSec {
		return false
	}
	r.count++
	return true
}
