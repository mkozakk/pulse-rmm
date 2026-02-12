//go:build linux

package desktop

import (
	"context"
	"errors"
	"fmt"
	"os"

	"github.com/pion/mediadevices"
	"github.com/pion/mediadevices/pkg/codec/vpx"
	"github.com/pion/mediadevices/pkg/prop"
)

func checkPlatform() error {
	if os.Getenv("WAYLAND_DISPLAY") != "" {
		return ErrWaylandNotSupported
	}
	return nil
}

func startCapture(sess *DesktopSession, ctx context.Context) error {
	if err := checkPlatform(); err != nil {
		return err
	}
	if os.Getenv("DISPLAY") == "" {
		return errors.New("no X11 display (DISPLAY not set)")
	}

	vpxParams, err := vpx.NewVP9Params()
	if err != nil {
		return fmt.Errorf("creating VP9 params: %w", err)
	}
	vpxParams.BitRate = 2_500_000

	codecSelector := mediadevices.NewCodecSelector(
		mediadevices.WithVideoEncoders(&vpxParams),
	)

	stream, err := mediadevices.GetDisplayMedia(mediadevices.MediaStreamConstraints{
		Video: func(c *mediadevices.MediaTrackConstraints) {
			c.FrameRate = prop.Float(30)
		},
		Codec: codecSelector,
	})
	if err != nil {
		return fmt.Errorf("getting display media: %w", err)
	}

	tracks := stream.GetVideoTracks()
	if len(tracks) == 0 {
		return errors.New("no video tracks from display")
	}

	track := tracks[0]
	if err := sess.addVideoTrack(track); err != nil {
		track.Close()
		return fmt.Errorf("adding video track to peer connection: %w", err)
	}

	go func() {
		<-ctx.Done()
		for _, t := range stream.GetVideoTracks() {
			t.Close()
		}
	}()

	return nil
}
