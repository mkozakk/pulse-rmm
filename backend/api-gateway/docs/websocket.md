# WebSocket Support (Not Implemented)

WebSocket proxying for remote sessions is not implemented in the API Gateway. This feature is planned for a future sprint.

### description
While the overall architecture envisions real-time interactive features like remote terminal sessions and WebRTC desktop control via WebSockets, this functionality is currently out of scope for the API Gateway. If and when implemented, a separate microservice (e.g., `remote-service`) would handle WebSocket upgrade, session management, and protocol translation between browser clients and agent gRPC streams.

The API Gateway's current role remains purely HTTP request routing with RBAC enforcement. Interactive real-time features should be implemented as standalone services that expose their own WebSocket endpoints, rather than being proxied through the gateway.
