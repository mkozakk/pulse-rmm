package desktop

import (
	"encoding/json"
	"fmt"
	"sync"
	"time"
)

type InputInjector interface {
	MouseMove(x, y int) error
	MouseButton(button int, pressed bool) error
	KeyEvent(keyCode int, pressed bool) error
	Close() error
}

type inputEvent struct {
	Type    string `json:"type"`
	X       int    `json:"x"`
	Y       int    `json:"y"`
	Button  int    `json:"button"`
	KeyCode int    `json:"keyCode"`
}

func parseInputEvent(data []byte) (inputEvent, error) {
	var ev inputEvent
	if err := json.Unmarshal(data, &ev); err != nil {
		return ev, fmt.Errorf("parsing input event: %w", err)
	}
	return ev, nil
}

// rateLimiter allows up to maxPerSec events per second, dropping the rest.
type rateLimiter struct {
	mu         sync.Mutex
	count      int
	windowStart time.Time
	maxPerSec  int
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

type noopInjector struct{}

func (n *noopInjector) MouseMove(x, y int) error            { return nil }
func (n *noopInjector) MouseButton(button int, pressed bool) error { return nil }
func (n *noopInjector) KeyEvent(keyCode int, pressed bool) error   { return nil }
func (n *noopInjector) Close() error                               { return nil }
