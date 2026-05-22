//go:build linux || windows

package capture

import (
	"bytes"
	"context"
	"errors"
	"io"
	"log"
	"testing"

	"github.com/pion/webrtc/v4"
	"github.com/stretchr/testify/require"
)

func TestNewOpusTrackHasMatchingCodec(t *testing.T) {
	track, err := newOpusTrack()
	require.NoError(t, err)
	require.NotNil(t, track)

	cap := track.Codec()
	require.Equal(t, webrtc.MimeTypeOpus, cap.MimeType)
	require.Equal(t, uint32(48000), cap.ClockRate)
	require.Equal(t, uint16(2), cap.Channels)
	require.Equal(t, "audio", track.Kind().String())
}

func TestRunAudioPipelineErrorsWhenAudioTrackHookMissing(t *testing.T) {
	target := Target{
		Logger:        log.New(io.Discard, "", 0),
		AddTrack:      func(webrtc.TrackLocal) error { return nil },
		AddAudioTrack: nil,
	}
	err := runAudioPipeline(context.Background(), target, []string{"-version"})
	require.ErrorIs(t, err, ErrAudioUnavailable)
}

func TestPumpOggOpusReturnsErrorOnGarbageStream(t *testing.T) {
	// pumpOggOpus opens the stream with oggreader.NewWith which validates
	// the OggS / OpusHead signatures. Random bytes must fail fast rather
	// than silently consuming the reader.
	track, err := newOpusTrack()
	require.NoError(t, err)

	junk := bytes.NewReader([]byte("not an ogg stream"))
	err = pumpOggOpus(context.Background(), junk, track, func(string, ...any) {})
	require.Error(t, err)
}

func TestPumpOggOpusReturnsOnContextCancel(t *testing.T) {
	track, err := newOpusTrack()
	require.NoError(t, err)

	// Build a minimal but valid OpusHead page so the reader's NewWith succeeds.
	// After that, pumpOggOpus loops on ParseNextPage which blocks on Read.
	// Cancelling ctx + closing the pipe makes the read return EOF; we accept
	// either the cancellation error or EOF as a clean stop.
	pr, pw := io.Pipe()
	go func() {
		pw.Write(buildOpusHeadPage())
		// leave the writer open so ParseNextPage blocks
	}()

	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan error, 1)
	go func() {
		done <- pumpOggOpus(ctx, pr, track, func(string, ...any) {})
	}()

	cancel()
	_ = pw.Close()

	err = <-done
	if err != nil && !errors.Is(err, context.Canceled) && !errors.Is(err, io.EOF) && !errors.Is(err, io.ErrClosedPipe) {
		t.Fatalf("expected clean stop, got %v", err)
	}
}

// buildOpusHeadPage returns the raw bytes for a single Ogg page carrying
// a valid OpusHead payload. Hand-built to avoid pulling extra deps just
// for tests.
func buildOpusHeadPage() []byte {
	payload := []byte("OpusHead")
	payload = append(payload,
		1,    // version
		2,    // channels (stereo)
		0, 0, // pre-skip (LE)
		0x80, 0xbb, 0, 0, // sample rate = 48000 (LE)
		0, 0, // output gain
		0, // channel mapping family
	)

	page := []byte{'O', 'g', 'g', 'S'}
	page = append(page,
		0,    // stream structure version
		0x02, // header type: beginning of stream
	)
	// granule position (8 bytes), serial (4), index (4)
	page = append(page,
		0, 0, 0, 0, 0, 0, 0, 0,
		1, 0, 0, 0,
		0, 0, 0, 0,
	)
	// checksum placeholder
	page = append(page, 0, 0, 0, 0)
	// segments
	page = append(page, 1, byte(len(payload)))
	page = append(page, payload...)

	// Fix checksum.
	tbl := newCrcTable()
	var crc uint32
	for i, b := range page {
		if i >= 22 && i < 26 {
			b = 0
		}
		crc = (crc << 8) ^ tbl[byte(crc>>24)^b]
	}
	page[22] = byte(crc)
	page[23] = byte(crc >> 8)
	page[24] = byte(crc >> 16)
	page[25] = byte(crc >> 24)
	return page
}

func newCrcTable() [256]uint32 {
	var t [256]uint32
	const poly = 0x04c11db7
	for i := range t {
		r := uint32(i) << 24
		for j := 0; j < 8; j++ {
			if r&0x80000000 != 0 {
				r = (r << 1) ^ poly
			} else {
				r <<= 1
			}
			t[i] = r
		}
	}
	return t
}
