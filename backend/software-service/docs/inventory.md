# Inventory Tracking (`api/`, `domain/`, `infrastructure/`)

Maintains a real-time, searchable database of all applications installed across the fleet by processing bulk telemetry from endpoints.

### code
**API & Routing**
[`SoftwareController.java`](../src/main/java/dev/pulsermm/software/api/SoftwareController.java):
	- `getInventory` - Exposes a public REST endpoint allowing the frontend web application to query the current software list for a specific machine.
[`SoftwareInternalController.java`](../src/main/java/dev/pulsermm/software/api/SoftwareInternalController.java):
	- `updateInventory` - Acts as an internal webhook receiver, accepting massive JSON payloads containing the complete software array reported by an agent through the API Gateway.

**Data Transfer Objects (DTOs)**
[`SoftwareItemResponse.java`](../src/main/java/dev/pulsermm/software/api/SoftwareItemResponse.java):
	- Structures the outbound JSON when the frontend requests the inventory list, mapping internal database fields into a clean, public-facing schema.

**Domain Entities & Repositories**
[`SoftwareItem.java`](../src/main/java/dev/pulsermm/software/domain/SoftwareItem.java):
	- The core domain entity representing a single application installed on a specific endpoint. It stores descriptive metadata such as the application name, version string, publisher, and installation date.
[`SoftwareItemRepository.java`](../src/main/java/dev/pulsermm/software/infrastructure/SoftwareItemRepository.java):
	- `deleteByEndpointId` & `saveAll` - Interfaces directly with the database to handle the complex synchronization process of the inventory list.

### description
To effectively manage IT environments, administrators require deep visibility into what software exists on their machines. The Go agents periodically scan their local operating systems (for example, querying the Windows Registry) and compile a comprehensive list of installed programs. They transmit this bulk data up the gRPC stream to the API Gateway. The Gateway then acts as a router, forwarding this payload via an internal HTTP POST to the `SoftwareInternalController` within the Software Service. 

Because applications are constantly updated, patched, or removed locally by end-users, tracking individual granular changes over the network is complex and error-prone. Instead, the service utilizes a declarative "sync" approach. When a new inventory payload arrives at the internal controller, it invokes the repository layer. The `SoftwareItemRepository` first deletes every existing `SoftwareItem` associated with that endpoint's UUID. It then immediately persists the newly received list as fresh entities via a bulk insert. This destructive sync ensures the database always perfectly mirrors the exact reality of the endpoint, automatically dropping records of uninstalled software and adding new ones without attempting complex delta calculations. The frontend can then query this synchronized state anytime via the `SoftwareController`.
