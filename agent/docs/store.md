# Local Storage (`internal/store/`)

Handles saving the agent's identity to the local hard drive.

### code
[`store.go`](../internal/store/store.go):
	- `LoadOrGenerateKey` - Retrieves the existing Ed25519 private key or securely generates a new one, ensuring a consistent cryptographic identity across reboots.
	- `SaveEndpointID` & `LoadEndpointID` - Manage the persistence of the server-assigned UUID, which the backend uses to uniquely identify the device.
	- `parsePrivateKey` & `savePrivateKey` - Handle PEM encoding and decoding to format raw byte slices securely for standard file system storage.

### description
To maintain a consistent identity across system reboots, the agent must persist its unique identifiers locally. This module abstracts all file system operations using the `os` and `path/filepath` packages to store and retrieve the agent's cryptographic material and endpoint UUID. When the agent starts, `LoadOrGenerateKey` attempts to read the private key from a designated secure directory using `os.ReadFile`. If the file is absent, it leverages the `crypto/ed25519` standard library to generate a new cryptographically secure keypair. The `savePrivateKey` helper is then invoked to wrap the raw key bytes in a PEM block using `encoding/pem` and write it to disk. Crucially, the module uses strict octal file permissions (`0600` for files, `0700` for directories) via `os.WriteFile` and `os.MkdirAll` to ensure that only the OS user running the agent can access these sensitive identity files, preventing local privilege escalation or identity theft.
