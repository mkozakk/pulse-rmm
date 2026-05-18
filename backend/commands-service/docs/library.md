# Script Library (`api/`, `application/`, `domain/`, `infrastructure/`)

Manages the core catalog of automation payloads, encompassing creation, approval workflows, and basic inventory retrieval.

### code
**API & Routing**
[`ScriptController.java`](../src/main/java/dev/pulsermm/script/api/controller/ScriptController.java):
	- `createScript`, `getScript`, & `listScripts` - Expose REST endpoints allowing administrators to populate the central library with new automation payloads, retrieve specific code bodies, and browse the available catalog.

**Data Transfer Objects (DTOs)**
[`CreateScriptRequest.java`](../src/main/java/dev/pulsermm/script/api/dto/CreateScriptRequest.java) & [`CreateScriptResponse.java`](../src/main/java/dev/pulsermm/script/api/dto/CreateScriptResponse.java):
	- Encapsulate the inbound payload (containing the raw code, target operating system, and required execution engine like PowerShell) and the outbound acknowledgment containing the newly generated database ID.
[`ScriptResponse.java`](../src/main/java/dev/pulsermm/script/api/dto/ScriptResponse.java) & [`ListScriptsResponse.java`](../src/main/java/dev/pulsermm/script/api/dto/ListScriptsResponse.java):
	- Structure the outbound JSON when the frontend requests details about a specific script or paginates through the entire catalog, explicitly omitting sensitive secrets from the public payload.

**Domain Exceptions**
[`ScriptNotFoundException.java`](../src/main/java/dev/pulsermm/script/application/ScriptNotFoundException.java):
	- Thrown when a request attempts to interact with an ID that does not exist in the database, triggering a standard 404 response.
[`ScriptAlreadyApprovedException.java`](../src/main/java/dev/pulsermm/script/application/ScriptAlreadyApprovedException.java):
	- Protects the integrity of the system by preventing administrators from modifying the raw code of a script after it has passed a formal approval workflow.

**Domain Entities & Repositories**
[`Script.java`](../src/main/java/dev/pulsermm/script/domain/Script.java):
	- The core domain entity representing the saved automation payload. It stores the raw text of the code, its required execution context, and administrative metadata such as approval status and authorship.
[`ScriptRepository.java`](../src/main/java/dev/pulsermm/script/infrastructure/persistence/ScriptRepository.java):
	- Interfaces with the PostgreSQL database to persist, retrieve, and search the script catalog.

### description
To effectively automate tasks across an enterprise fleet, IT staff require a centralized, version-controlled library of scripts. When an administrator utilizes the web interface to define a new task, the request routes to the `ScriptController` as a `CreateScriptRequest`. This payload is translated into a `Script` domain entity and persisted via the `ScriptRepository`. 

Because running arbitrary code on remote endpoints is inherently dangerous, the `Script` entity includes states for approval workflows. If an administrator attempts to alter the code of a script that has already been verified and locked, the application layer throws a `ScriptAlreadyApprovedException`, rejecting the modification to guarantee that the executed code matches what was audited. When the frontend needs to render the available scripts, it hits the catalog endpoints, which utilize `ScriptResponse` and `ListScriptsResponse` to project the database entities into safe, read-only JSON structures, ensuring no internal execution flags leak to unauthorized users.
