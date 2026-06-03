# Known Issues & Solutions

## Testcontainers with Podman

**Problem:** Testcontainers looks for Docker socket by default.

**Solution:**
```bash
systemctl --user enable --now podman.socket
export DOCKER_HOST=unix:///run/user/$(id -u)/podman/podman.sock
export TESTCONTAINERS_RYUK_DISABLED=true
export TESTCONTAINERS_CHECKS_DISABLE=true
```

---

## gRPC Service Not Registered in Spring Boot

**Problem:** `@GrpcService` annotated class compiles and is in the JAR, but Spring doesn't register it.

**Solution:** Container image caching. Remove old image and rebuild:
```bash
podman rmi docker.io/library/pulse-e2e-api-gateway:latest
podman compose -f deploy/compose.yaml -f deploy/compose.e2e.yaml build api-gateway
podman compose down -v && podman compose up -d
```

---

## WebRTC H.264 Black Screen

**Problem:** Agent streams H.264 to browser, but Chrome shows black screen (framesDecoded=0, framesReceived=0).

**Root causes and fixes:**
1. **Corrupted NAL units** - Custom NAL parser appended next start code to current NAL. Use `pion/webrtc/v4/pkg/media/h264reader` instead.
2. **Per-NAL samples** - Each NAL was emitted as separate RTP sample, causing different timestamps. Accumulate NALs into one sample per frame.
3. **SDP profile mismatch** - SDP had `profile-level-id=42001f` but libx264 outputs `42e01f`. Update SDP to match.
4. **Missing parameter sets** - SPS/PPS only sent at stream start. Add `-bsf:v dump_extra` to FFmpeg to repeat before every keyframe.

---

## H.264 Bottom Half Blurry

**Problem:** After decode starts, bottom half of frame is blurred/stale.

**Solution:** libx264 with `-tune zerolatency` enables multi-slice frames. Disable with:
```bash
-x264-params slices=1:sliced-threads=0
```

---

## FFmpeg Process Leaks After Session Ends

**Problem:** Two `HandleStartSession` calls race; one FFmpeg spawned, but only one tracked. Untracked FFmpeg keeps running and pegs CPU.

**Solution:** Add session-level locking to prevent concurrent starts on same session ID.

---

## Wayland Desktop Capture Requires User Consent

**Problem:** Pipewire portal requires interactive user approval per capture session.

**Impact:** Unattended desktop access not supported on Wayland. Only supported with logged-in user who approves the permission prompt.

---

## Agent Service Must Run in User Session

**Problem:** Agent registered as system service doesn't capture desktop in user session.

**Solution:** Agent must run in the user's session context (systemd --user, not system-wide service).

---

## Keycloak Health Check

**Problem:** Keycloak image doesn't have `curl` for HTTP health checks.

**Solution:** Use TCP health checks instead:
```yaml
healthcheck:
  test: ["CMD", "nc", "-z", "localhost", "8080"]
```

---

## Keycloak User First/Last Name

**Problem:** Keycloak doesn't auto-populate first/last name on user creation.

**Solution:** Set explicitly via Admin API or accept empty values (fields are optional per Keycloak design).
