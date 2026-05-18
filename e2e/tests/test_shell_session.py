import time

import pytest
import websocket

pytestmark = [pytest.mark.slow, pytest.mark.requires_agent]

# Auth enforcement tests consolidated in test_auth_enforcement.py


def test_remote_shell_executes_command(admin_session, enrolled_agent):
    collected = b""
    deadline = time.time() + 20
    while time.time() < deadline:
        ws = websocket.WebSocket()
        ws.settimeout(5)
        try:
            ws.connect(f"ws://localhost:8080/ws/shell/{enrolled_agent}?token={admin_session.token}")
            ws.send_binary(b"\x01" + b"echo e2ehello\n")
            inner = time.time() + 8
            while time.time() < inner:
                try:
                    frame = ws.recv()
                    if isinstance(frame, bytes) and len(frame) > 1 and frame[0] == 0x01:
                        collected += frame[1:]
                    if b"e2ehello" in collected:
                        return
                except websocket.WebSocketTimeoutException:
                    break
        except Exception as e:
            print(f"[test] attempt failed: {e}")
        finally:
            try:
                ws.close()
            except Exception:
                pass
        time.sleep(1)

    pytest.fail(f"'e2ehello' not in shell output after 20s. Collected: {collected!r}")


def test_shell_invalid_token_rejected(enrolled_agent):
    """WebSocket connection with an invalid token should be rejected."""
    ws = websocket.WebSocket()
    ws.settimeout(5)
    rejected = False
    try:
        ws.connect(f"ws://localhost:8080/ws/shell/{enrolled_agent}?token=invalid-token")
        ws.settimeout(2)
        try:
            ws.recv()
        except Exception:
            pass
        status = ws.status
        rejected = status in (None, 4001, 1008, 1002, 401, 403)
    except Exception:
        rejected = True  # connection refused or immediate close
    finally:
        try:
            ws.close()
        except Exception:
            pass

    assert rejected, "expected connection to be rejected with invalid token"
