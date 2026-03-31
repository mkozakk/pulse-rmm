# Authentication & Sessions (`api/`, `application/`, `domain/`, `infrastructure/`)

Manages user identity lifecycle, cryptographic token issuance, session rotation, and authentication error handling.

### code
**API & Routing**
[`AuthController.java`](../src/main/java/dev/pulsermm/identity/api/AuthController.java):
	- `login` & `register` - Expose public endpoints for verifying credentials and bootstrapping new accounts.
	- `refresh` - Provides a secure, cookie-based mechanism to rotate short-lived access tokens without exposing refresh tokens to the frontend application.
	- `logout` - Revokes the active refresh token and clears the client's HTTP-only cookie to terminate the session securely.

**Data Transfer Objects (DTOs)**
[`LoginRequest.java`](../src/main/java/dev/pulsermm/identity/api/dto/LoginRequest.java) & [`RegisterRequest.java`](../src/main/java/dev/pulsermm/identity/api/dto/RegisterRequest.java):
	- Encapsulate the inbound JSON payloads containing user credentials during initial authentication and account creation.
[`RegisterResponse.java`](../src/main/java/dev/pulsermm/identity/api/dto/RegisterResponse.java) & [`TokenResponse.java`](../src/main/java/dev/pulsermm/identity/api/dto/TokenResponse.java):
	- Structure the outbound JSON containing public user profile data and newly minted short-lived access tokens.
[`LoginResult.java`](../src/main/java/dev/pulsermm/identity/application/LoginResult.java) & [`RotatedRefreshToken.java`](../src/main/java/dev/pulsermm/identity/application/RotatedRefreshToken.java):
	- Internal records utilized by the application layer to cleanly pass multi-part authentication states (like paired access and refresh tokens) back to the presentation layer.

**Application Logic**
[`AuthService.java`](../src/main/java/dev/pulsermm/identity/application/AuthService.java):
	- `authenticate` - Validates plain-text passwords against securely hashed representations stored in the database, abstracting BCrypt complexity.
	- `registerUser` - Enforces username uniqueness and password strength constraints before persisting a new identity to the repository.

[`JwtService.java`](../src/main/java/dev/pulsermm/identity/application/JwtService.java) & [`JwtProperties.java`](../src/main/java/dev/pulsermm/identity/application/JwtProperties.java):
	- `generateToken` - Constructs cryptographically signed JSON Web Tokens embedding the user's UUID and roles, ensuring implicit trust by the API Gateway.
	- `JwtProperties` - Binds JWT configuration parameters (secret keys, expiration times) from the application environment into a strongly typed configuration record.

[`RefreshTokenService.java`](../src/main/java/dev/pulsermm/identity/application/RefreshTokenService.java) & [`RefreshTokenRevocationService.java`](../src/main/java/dev/pulsermm/identity/application/RefreshTokenRevocationService.java):
	- `createRefreshToken` & `rotateToken` - Generate secure, long-lived tokens and handle the logic of replacing an old token with a new one during a refresh cycle.
	- `revokeDescendants` - Implements token theft detection by invalidating entire chains of refresh tokens if an already-used token is presented again.

**Domain & Infrastructure**
[`User.java`](../src/main/java/dev/pulsermm/identity/domain/User.java) & [`UserRepository.java`](../src/main/java/dev/pulsermm/identity/infrastructure/UserRepository.java):
	- `User` - The core domain entity representing a human administrator or technician, storing their unique identifier and BCrypt password hash.
	- `UserRepository` - Interfaces with the database to persist, retrieve, and query user records by username.

[`RefreshToken.java`](../src/main/java/dev/pulsermm/identity/domain/RefreshToken.java) & [`RefreshTokenRepository.java`](../src/main/java/dev/pulsermm/identity/infrastructure/RefreshTokenRepository.java):
	- `RefreshToken` - Represents a cryptographically secure token stored in the database, tracking its expiration date and revocation status for long-term session persistence.
	- `RefreshTokenRepository` - Handles database transactions for token issuance, lookup by value, and batch revocation.

**Domain Exceptions**
[`InvalidCredentialsException.java`](../src/main/java/dev/pulsermm/identity/api/errors/InvalidCredentialsException.java) & [`InvalidRefreshTokenException.java`](../src/main/java/dev/pulsermm/identity/api/errors/InvalidRefreshTokenException.java):
	- Thrown when password verification fails or when a manipulated/expired refresh token is presented, triggering a 401 Unauthorized response.
[`UsernameTakenException.java`](../src/main/java/dev/pulsermm/identity/api/errors/UsernameTakenException.java) & [`BootstrapClosedException.java`](../src/main/java/dev/pulsermm/identity/api/errors/BootstrapClosedException.java):
	- Thrown to prevent duplicate accounts and to secure the initial admin registration flow once the system has been successfully bootstrapped.

### description
To ensure a secure architecture, the infrastructure relies on JSON Web Tokens (JWTs) generated exclusively by the Identity Service. When a user inputs their credentials, the data arrives encapsulated in a `LoginRequest`. The `AuthController` routes this to the `AuthService`, which queries the `UserRepository` and utilizes BCrypt hashing to verify the password without ever exposing plain-text secrets in memory. Upon verification, the `JwtService` constructs a signed access token. This token embeds necessary context, such as the user's ID, acting as an immutable passport. Because this access token has a very short lifespan, the system relies on the `RefreshTokenService` to maintain active sessions. During login, this service generates a `RefreshToken` entity and the controller securely attaches it as an `HttpOnly` cookie. When the frontend's access token expires, it silently hits the refresh endpoint. If valid, it issues a `RotatedRefreshToken` alongside a fresh JWT. Crucially, the `RefreshTokenRevocationService` monitors for token theft. If a malicious actor attempts to reuse an older, already-rotated refresh token, the service detects the anomaly, throws an `InvalidRefreshTokenException`, and immediately revokes the entire token family, forcing all active sessions for that user to terminate.
