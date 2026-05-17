package input

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestNoopInjectorDoesNotPanic(t *testing.T) {
	inj := Noop()
	assert.NoError(t, inj.MouseMove(100, 200))
	assert.NoError(t, inj.MouseButton(0, true))
	assert.NoError(t, inj.KeyEvent(65, true))
	assert.NoError(t, inj.Close())
}
