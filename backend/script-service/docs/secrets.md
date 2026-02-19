# Secret Management (`application/`, `domain/`, `infrastructure/`)

Ensures sensitive variables used within scripts are cryptographically secured at rest in the database and safely injected during execution.

### code
**Domain Exceptions**
[`SecretEncryptionException.java`](../src/main/java/dev/pulsermm/script/application/SecretEncryptionException.java) & [`SecretDecryptionException.java`](../src/main/java/dev/pulsermm/script/application/SecretDecryptionException.java):
	- Thrown when the cryptographic algorithms fail (e.g., due to a corrupted master key or malformed ciphertext), halting the script execution process immediately to prevent leaking garbage data to agents.

**Application Logic & Cryptography**
[`EncryptionConfig.java`](../src/main/java/dev/pulsermm/script/infrastructure/config/EncryptionConfig.java):
	- Extracts the master cryptographic key from the secure environment configuration, ensuring the key is never hardcoded within the application source files.
[`ScriptSecretEncryptor.java`](../src/main/java/dev/pulsermm/script/infrastructure/security/ScriptSecretEncryptor.java):
	- Implements robust AES symmetric encryption and decryption algorithms, transforming plain text into unreadable ciphertext prior to persistence, and reversing the process when data is required in memory.

**Domain Entities & Repositories**
[`ScriptSecret.java`](../src/main/java/dev/pulsermm/script/domain/ScriptSecret.java):
	- An entity designed specifically to store sensitive key-value pairs linked to a parent script. It guarantees that the database only ever stores the encrypted representation of the value.
[`ScriptSecretRepository.java`](../src/main/java/dev/pulsermm/script/infrastructure/persistence/ScriptSecretRepository.java):
	- Interfaces with the database to store the ciphertexts and retrieve them based on the script identifier.

### description
Scripts frequently require sensitive data to function, such as API tokens, local administrator passwords, or database credentials. Storing these values as plain text within the `Script` entity in the PostgreSQL database introduces a critical security vulnerability. To mitigate this risk, the Script Service implements application-layer cryptography. 

When an administrator saves a script containing sensitive variables, the `ScriptSecretEncryptor` utilizes the master key provided by the `EncryptionConfig` to perform AES encryption on the plain text string. The resulting ciphertext is persisted inside a `ScriptSecret` entity via the `ScriptSecretRepository`. Even if a malicious actor completely compromises the PostgreSQL database, the secrets remain mathematically unreadable. Later, when the script is queued for execution by the `ScriptService`, the service retrieves the `ScriptSecret` entities. It passes the ciphertexts back through the `ScriptSecretEncryptor` to decrypt them into plain text in memory. These raw values are then immediately injected into the outbound command payload destined for the API Gateway. This architecture ensures that the remote agent receives the necessary credentials to execute the task, but the database itself never exposes the raw values.
