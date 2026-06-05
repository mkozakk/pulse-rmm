# File Transfer (`infrastructure/file/`)

Coordinates file uploads and downloads during remote desktop sessions via WebRTC data channels.

## Overview

File transfer happens inside a WebRTC data channel named `file-transfer`, which is opened alongside the video/audio/input channels. This keeps file transfers from blocking the input channel (mouse/keyboard) if a large file is being transferred.

The browser initiates uploads/downloads via HTTP REST endpoints; the agent handles the actual file I/O.

## Upload (Browser → Endpoint)

### HTTP Request

```
POST /api/sessions/{sessionId}/files/upload
Content-Type: multipart/form-data

file: <binary data>
```

### Flow

1. Browser makes POST request with file content
2. Endpoint-service creates an upload command with the file content and destination path
3. Endpoint-service calls agent-hub: `POST /internal/agents/{endpointId}/file-upload`
4. Agent-hub creates a `FileTransferCommand` with command ID and metadata
5. Agent receives command, validates destination path (must be within user's home directory)
6. Agent reads file from the WebRTC data channel with matching command ID
7. Agent writes file to disk
8. Agent sends `FileTransferResult` back to hub
9. Hub forwards result to endpoint-service
10. Browser receives `200 OK` with upload result

### Path Traversal Protection

The agent validates the destination path:
- Must be within the user's home directory (e.g., `/home/alice/...` on Linux, `C:\Users\alice\...` on Windows)
- Rejects paths like `../../../etc/passwd` or absolute paths outside home
- Symlinks are followed only within the home directory boundary

## Download (Endpoint → Browser)

### HTTP Request

```
GET /api/sessions/{sessionId}/files/{path}
```

### Flow

1. Browser makes GET request with file path relative to home directory
2. Endpoint-service creates a download command with the source path
3. Endpoint-service calls agent-hub: `POST /internal/agents/{endpointId}/file-download`
4. Agent-hub creates a `FileTransferCommand` with command ID and metadata
5. Agent receives command, validates source path
6. Agent reads file from disk
7. Agent sends file content over the WebRTC data channel with matching command ID
8. Hub receives the data and buffers it
9. Browser downloads the file via HTTP

### Path Traversal Protection

Same as upload: files can only be accessed within the user's home directory. Paths like `../../../etc/passwd` are rejected.

## Data Channel Coordination

**FileTransferRegistry** (`FileTransferRegistry.java`)
- Maps command IDs to in-progress transfers
- Holds metadata: file size, destination/source path, checksum
- Timeouts old transfers (if client disconnects)

**Message Format (gRPC):**

Hub → Agent:
```protobuf
message FileTransferCommand {
  string commandId = 1;
  enum Operation { UPLOAD = 0; DOWNLOAD = 1; }
  Operation op = 2;
  string path = 3;
  uint64 sizeBytes = 4;
  string checksum = 5; // SHA256
}
```

Agent → Hub:
```protobuf
message FileTransferResult {
  string commandId = 1;
  bool success = 2;
  string error = 3; // if success=false
  string checksum = 4; // if success=true
}
```

## Checksum Verification

After transfer, both sides calculate SHA256 of the file content:
- Agent calculates checksum while reading/writing
- Hub or browser calculates checksum while receiving/sending
- If checksums don't match, the transfer failed (corruption detected)
- Browser retries the transfer

## Performance Considerations

**Concurrent Transfers**
- The `file-transfer` data channel is separate from the `input` channel, so file transfers don't block keyboard input
- Multiple file transfers can run in parallel if multiple data channels are created (not currently implemented)

**Large File Streaming**
- Files are streamed in chunks (e.g., 64 KB per message)
- Chunking prevents the entire file from being buffered in memory
- Progress updates are sent via SSE or polling

**Bandwidth Limiting**
- File transfers use the same WebRTC connection as video, so they share bandwidth
- Network quality may degrade video quality if a large file is being transferred simultaneously

## Limitations

- **Only home directory files** — Desktop sessions can only upload/download files within the user's home directory. System files, root-owned files, etc. are inaccessible.
- **No directory listing** — Clients cannot list directories; they must know the file path to download
- **No permissions enforcement** — The agent runs as root/SYSTEM, so it can write files even if the user wouldn't normally have permission (by design, this is a remote access tool)

## Security Considerations

**Path Traversal** — Validated at the agent side. Symlinks within home directory are OK; symlinks escaping home are rejected.

**File Integrity** — Checksums verify files weren't corrupted during transfer.

**Encryption in Transit** — Files are encrypted by the WebRTC connection (DTLS), not by the file-transfer protocol itself. Additionally, the agent-hub ↔ agent connection is mTLS-encrypted.

**Sensitive Files** — Files containing secrets (e.g., SSH keys, API tokens) can be downloaded by any technician with `endpoint:session:create` permission. The audit log records who downloaded what.

## Related Components

- **[Session Management](session-management.md)** — File transfers only work within active desktop sessions
- **[WebRTC (agent)](../../agent/docs/desktop.md)** — Agent-side file handling in the desktop module
