package desktop

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestParseMouseMoveEvent(t *testing.T) {
	ev, err := parseInputEvent([]byte(`{"type":"mousemove","x":100,"y":200}`))
	require.NoError(t, err)
	assert.Equal(t, "mousemove", ev.Type)
	assert.Equal(t, 100, ev.X)
	assert.Equal(t, 200, ev.Y)
}

func TestParseKeyEvent(t *testing.T) {
	ev, err := parseInputEvent([]byte(`{"type":"keydown","keyCode":65}`))
	require.NoError(t, err)
	assert.Equal(t, "keydown", ev.Type)
	assert.Equal(t, 65, ev.KeyCode)
}
