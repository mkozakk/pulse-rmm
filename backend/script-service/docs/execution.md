# Execution & Routing (`api/`, `application/`, `domain/`, `infrastructure/`)

Manages the dispatching of scripts to agents, tracks the real-time state of the execution, and permanently records the terminal output.

### code
**Data Transfer Objects (DTOs)**
[`RunScriptRequest.java`](../src/main/java/dev/pulsermm/script/api/dto/RunScriptRequest.java) & [`InitiateScriptRunResponse.java`](../src/main/java/dev/pulsermm/script/api/dto/InitiateScriptRunResponse.java):
	- Capture the administrator's intent to execute a specific script against a specific endpoint, returning an immediate tracking ticket ID so the frontend can poll for progress.
[`ScriptRunResponse.java`](../src/main/java/dev/pulsermm/script/api/dto/ScriptRunResponse.java) & [`ScriptRunResultResponse.java`](../src/main/java/dev/pulsermm/script/api/dto/ScriptRunResultResponse.java):
	- Expose the ongoing lifecycle status of a dispatch (Pending, Running, Completed) and the final standard output/error text returned by the remote machine.
[`CommandAckRequest.java`](../src/main/java/dev/pulsermm/script/api/dto/CommandAckRequest.java):
	- Acts as an internal webhook payload structure, used by the API Gateway to asynchronously inform the Script Service when an agent acknowledges receipt or completes an execution.

**Domain Exceptions**
[`ScriptRunNotFoundException.java`](../src/main/java/dev/pulsermm/script/application/ScriptRunNotFoundException.java) & [`ScriptRunResultNotFoundException.java`](../src/main/java/dev/pulsermm/script/application/ScriptRunResultNotFoundException.java):
	- Thrown when polling requests target invalid tracking IDs, preventing NullPointerExceptions during asynchronous status checks.

**Application Logic & Integrations**
[`ScriptService.java`](../src/main/java/dev/pulsermm/script/application/ScriptService.java):
	- The core orchestrator that handles the complex workflow of fetching the script code, creating the tracking database records, decrypting necessary secrets, and issuing the network command.
[`GatewayClient.java`](../src/main/java/dev/pulsermm/script/infrastructure/GatewayClient.java):
	- An internal HTTP client specifically configured to route the fully assembled script payload to the API Gateway's internal dispatch controller, abstracting away the underlying gRPC stream mechanics.

**Domain Entities & Repositories**
[`ScriptRun.java`](../src/main/java/dev/pulsermm/script/domain/ScriptRun.java) & [`ScriptRunRepository.java`](../src/main/java/dev/pulsermm/script/infrastructure/persistence/ScriptRunRepository.java):
	- Act as a tracking ticket representing a specific instance of a script being dispatched, monitoring its state transitions over time.
[`ScriptRunResult.java`](../src/main/java/dev/pulsermm/script/domain/ScriptRunResult.java) & [`ScriptRunResultRepository.java`](../src/main/java/dev/pulsermm/script/infrastructure/persistence/ScriptRunResultRepository.java):
	- Permanently store the final standard output (stdout), standard error (stderr), and exit code returned by the agent.

### description
Triggering a script execution is a complex, asynchronous process. When an administrator initiates a task, the `RunScriptRequest` is processed by the `ScriptService`. Because network connections are unreliable and script execution times vary wildly, the service does not wait for the agent to finish. Instead, it generates a `ScriptRun` tracking record in the database, setting its initial status to pending, and immediately returns the tracking ID to the frontend via `InitiateScriptRunResponse`. 

The `ScriptService` then utilizes the `GatewayClient` to push an internal HTTP request containing the raw code to the API Gateway. The Gateway routes this payload down the appropriate gRPC stream to the physical agent. When the agent finishes executing the task, it sends a gRPC event back to the Gateway. The Gateway translates this event into a `CommandAckRequest` and fires it as an internal webhook back to the Script Service's controller. The service intercepts this acknowledgment, updates the `ScriptRun` state to completed, and persists the raw terminal output into a `ScriptRunResult` entity via the `ScriptRunResultRepository`. This guarantees administrators possess a permanent, immutable audit trail of exactly what the script outputted on the remote machine.
