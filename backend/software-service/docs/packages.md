# Package Management (`api/`, `application/`, `domain/`, `infrastructure/`)

Orchestrates the asynchronous installation and uninstallation of software on remote endpoints using the system's internal command delivery pipeline.

### code
**API & Routing**
[`SoftwareController.java`](../src/main/java/dev/pulsermm/software/api/SoftwareController.java):
	- `installSoftware` & `removeSoftware` - Expose the primary REST endpoints allowing administrators to dispatch package management directives to the fleet.

**Data Transfer Objects (DTOs)**
[`InstallRequest.java`](../src/main/java/dev/pulsermm/software/api/InstallRequest.java) & [`RemoveRequest.java`](../src/main/java/dev/pulsermm/software/api/RemoveRequest.java):
	- Encapsulate the inbound payloads defining the specific package name, version constraints, and target endpoint required to execute the remote task.

**Application Logic & Integrations**
[`SoftwareService.java`](../src/main/java/dev/pulsermm/software/application/SoftwareService.java):
	- The central orchestrator that validates the administrator's request, generates the persistent tracking records, and initiates the internal network call to dispatch the instruction.
[`GatewayClient.java`](../src/main/java/dev/pulsermm/software/infrastructure/GatewayClient.java):
	- An internal HTTP client specifically configured to route the installation parameters to the API Gateway's internal dispatch controller, completely abstracting away the underlying gRPC network mechanics from the application logic.

**Domain Entities & Repositories**
[`SoftwareCommand.java`](../src/main/java/dev/pulsermm/software/domain/SoftwareCommand.java):
	- An entity representing an administrative intent to alter the software state of an endpoint, permanently tracking the package name, the requested action (Install/Remove), and the current asynchronous execution status.
[`SoftwareCommandRepository.java`](../src/main/java/dev/pulsermm/software/infrastructure/SoftwareCommandRepository.java):
	- Persists the tracking commands to the database, ensuring administrators maintain a historical audit log of all attempted software deployments.

### description
Beyond merely observing inventory, the system allows administrators to actively manipulate it. When an administrator initiates a software installation (e.g., deploying a package via Chocolatey or Winget on Windows) from the web interface, the `InstallRequest` hits the `SoftwareController` and is passed to the `SoftwareService`. Because network conditions are highly variable and actual software installations can take significant time to complete on the target machine, the system cannot block the HTTP thread and wait for a synchronous response. 

Instead, it employs an asynchronous command tracking pattern. The service first creates a `SoftwareCommand` entity in the database via the `SoftwareCommandRepository`, marking its status as "Pending." This provides a permanent tracking record of the administrator's intent. The service then leverages the `GatewayClient` to send the installation parameters via internal HTTP to the API Gateway. The Gateway assumes responsibility for translating this intent into a Protobuf message and pushing it down the persistent gRPC stream to the specific agent. The agent executes the package manager locally and eventually returns an acknowledgment or error code via the same stream. The Gateway routes this event back to the backend, eventually updating the `SoftwareCommand` status to "Completed" or "Failed," allowing the administrator to verify the final outcome.
