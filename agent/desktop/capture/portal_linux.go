//go:build linux

package capture

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"strings"

	"github.com/godbus/dbus/v5"
)

const (
	portalDest      = "org.freedesktop.portal.Desktop"
	portalPath      = "/org/freedesktop/portal/desktop"
	screencastIface = "org.freedesktop.portal.ScreenCast"
	requestIface    = "org.freedesktop.portal.Request"
)

// Portal source types (bitfield, ScreenCast spec).
const (
	sourceTypeMonitor = uint32(1)
)

// Cursor modes (bitfield, ScreenCast spec).
const (
	cursorModeEmbedded = uint32(2)
)

// screencastSession is what we hand to the capture pipeline. Close() ends
// the portal session and the user's "is being shared" indicator goes away.
type screencastSession struct {
	bus     *dbus.Conn
	handle  dbus.ObjectPath
	nodeIDs []uint32
}

func (s *screencastSession) Close() {
	if s.bus == nil {
		return
	}
	if s.handle != "" {
		_ = s.bus.Object(portalDest, s.handle).Call("org.freedesktop.portal.Session.Close", 0).Err
	}
	_ = s.bus.Close()
}

// openScreencast walks the portal flow: CreateSession → SelectSources →
// Start. On success the returned struct contains one or more PipeWire node
// IDs the user agreed to share.
func openScreencast(ctx context.Context) (*screencastSession, error) {
	bus, err := dbus.SessionBus()
	if err != nil {
		return nil, fmt.Errorf("connecting to session bus: %w", err)
	}

	// Check the portal is actually on the bus before issuing calls — otherwise
	// dbus blocks until activation timeout, which is a worse UX than failing
	// fast with a clear "install xdg-desktop-portal" message.
	var owner string
	if err := bus.BusObject().CallWithContext(ctx, "org.freedesktop.DBus.GetNameOwner", 0, portalDest).Store(&owner); err != nil {
		_ = bus.Close()
		return nil, ErrPortalNotInstalled
	}

	sender := strings.ReplaceAll(strings.TrimPrefix(bus.Names()[0], ":"), ".", "_")
	obj := bus.Object(portalDest, dbus.ObjectPath(portalPath))

	sigCh := make(chan *dbus.Signal, 16)
	bus.Signal(sigCh)
	defer bus.RemoveSignal(sigCh)

	if err := bus.AddMatchSignal(
		dbus.WithMatchInterface(requestIface),
		dbus.WithMatchMember("Response"),
	); err != nil {
		_ = bus.Close()
		return nil, fmt.Errorf("subscribing to portal responses: %w", err)
	}

	sessionToken := randomToken()
	sessionHandle := dbus.ObjectPath(fmt.Sprintf("/org/freedesktop/portal/desktop/session/%s/%s", sender, sessionToken))

	// 1. CreateSession
	createOpts := map[string]dbus.Variant{
		"handle_token":         dbus.MakeVariant(randomToken()),
		"session_handle_token": dbus.MakeVariant(sessionToken),
	}
	if _, err := portalCall(ctx, bus, obj, sigCh, sender, screencastIface+".CreateSession", createOpts); err != nil {
		_ = bus.Close()
		return nil, err
	}

	// 2. SelectSources
	selectOpts := map[string]dbus.Variant{
		"handle_token": dbus.MakeVariant(randomToken()),
		"types":        dbus.MakeVariant(sourceTypeMonitor),
		"multiple":     dbus.MakeVariant(false),
		"cursor_mode":  dbus.MakeVariant(cursorModeEmbedded),
	}
	if _, err := portalCall(ctx, bus, obj, sigCh, sender, screencastIface+".SelectSources", sessionHandle, selectOpts); err != nil {
		_ = bus.Close()
		return nil, err
	}

	// 3. Start (this is the one that pops the consent dialog)
	startOpts := map[string]dbus.Variant{
		"handle_token": dbus.MakeVariant(randomToken()),
	}
	results, err := portalCall(ctx, bus, obj, sigCh, sender, screencastIface+".Start", sessionHandle, "", startOpts)
	if err != nil {
		_ = bus.Close()
		return nil, err
	}

	streamsVar, ok := results["streams"]
	if !ok {
		_ = bus.Close()
		return nil, ErrPortalNoStream
	}

	// streams is a(ua{sv}) — array of (node_id, properties).
	type streamEntry struct {
		NodeID uint32
		Props  map[string]dbus.Variant
	}
	var streams []streamEntry
	if err := streamsVar.Store(&streams); err != nil {
		_ = bus.Close()
		return nil, fmt.Errorf("parsing portal streams: %w", err)
	}
	if len(streams) == 0 {
		_ = bus.Close()
		return nil, ErrPortalNoStream
	}

	nodes := make([]uint32, 0, len(streams))
	for _, s := range streams {
		nodes = append(nodes, s.NodeID)
	}

	return &screencastSession{
		bus:     bus,
		handle:  sessionHandle,
		nodeIDs: nodes,
	}, nil
}

// portalCall invokes a portal method that returns a Request object handle,
// then waits for the Request.Response signal and returns its results dict.
// The options dict (always the last argument) must contain a handle_token
// so we can predict the Request path and filter signals to just ours.
func portalCall(
	ctx context.Context,
	bus *dbus.Conn,
	obj dbus.BusObject,
	sigCh chan *dbus.Signal,
	sender string,
	method string,
	args ...interface{},
) (map[string]dbus.Variant, error) {
	opts, ok := args[len(args)-1].(map[string]dbus.Variant)
	if !ok {
		return nil, fmt.Errorf("portal call %s: last arg is not options dict", method)
	}
	tokenVar, ok := opts["handle_token"]
	if !ok {
		return nil, fmt.Errorf("portal call %s: options missing handle_token", method)
	}
	token, ok := tokenVar.Value().(string)
	if !ok {
		return nil, fmt.Errorf("portal call %s: handle_token is not a string", method)
	}
	expectedReq := dbus.ObjectPath(fmt.Sprintf("/org/freedesktop/portal/desktop/request/%s/%s", sender, token))

	var actualReq dbus.ObjectPath
	if err := obj.CallWithContext(ctx, method, 0, args...).Store(&actualReq); err != nil {
		return nil, fmt.Errorf("calling %s: %w", method, err)
	}

	for {
		select {
		case <-ctx.Done():
			return nil, ErrConsentTimeout
		case sig := <-sigCh:
			if sig.Path != expectedReq && sig.Path != actualReq {
				continue
			}
			if sig.Name != requestIface+".Response" {
				continue
			}
			if len(sig.Body) < 2 {
				return nil, fmt.Errorf("portal %s: malformed response signal", method)
			}
			response, ok := sig.Body[0].(uint32)
			if !ok {
				return nil, fmt.Errorf("portal %s: response code is not a uint32", method)
			}
			results, _ := sig.Body[1].(map[string]dbus.Variant)
			switch response {
			case 0:
				return results, nil
			case 1:
				return nil, ErrConsentDenied
			default:
				return nil, fmt.Errorf("portal %s: response code %d", method, response)
			}
		}
	}
}

func randomToken() string {
	b := make([]byte, 8)
	_, _ = rand.Read(b)
	return "pulse_" + hex.EncodeToString(b)
}
