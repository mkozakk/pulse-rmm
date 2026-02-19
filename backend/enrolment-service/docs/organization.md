# Organization & Tagging (`api/`, `application/`, `domain/`, `infrastructure/`)

Manages the logical structure of the IT fleet through rigid hierarchical groups and highly flexible dynamic tagging mechanisms.

### code
**API & Routing**
[`GroupController.java`](../src/main/java/dev/pulsermm/enrolment/api/controller/GroupController.java):
	- `createGroup`, `moveEndpoint`, & `getGroups` - Expose REST endpoints for defining the organizational folder structure and physically relocating endpoints between them.
[`TagRuleController.java`](../src/main/java/dev/pulsermm/enrolment/api/controller/TagRuleController.java):
	- `createRule` & `applyTags` - Provide interfaces allowing administrators to define automated tagging logic or manually force specific labels onto specific endpoints.

**Data Transfer Objects (DTOs)**
[`CreateGroupRequest.java`](../src/main/java/dev/pulsermm/enrolment/api/dto/CreateGroupRequest.java) & [`GroupResponse.java`](../src/main/java/dev/pulsermm/enrolment/api/dto/GroupResponse.java):
	- Structure the payloads required to generate new hierarchical branches and return the visual representation of the group tree to the frontend.
[`MoveEndpointRequest.java`](../src/main/java/dev/pulsermm/enrolment/api/dto/MoveEndpointRequest.java):
	- Encapsulates the intent to alter an endpoint's primary group affiliation.
[`CreateTagRuleRequest.java`](../src/main/java/dev/pulsermm/enrolment/api/dto/CreateTagRuleRequest.java), [`TagRuleResponse.java`](../src/main/java/dev/pulsermm/enrolment/api/dto/TagRuleResponse.java), [`SetTagsRequest.java`](../src/main/java/dev/pulsermm/enrolment/api/dto/SetTagsRequest.java), & [`TagEntry.java`](../src/main/java/dev/pulsermm/enrolment/api/dto/TagEntry.java):
	- Represent the complex criteria definitions, responses, and manual mapping payloads utilized to associate arbitrary string labels with endpoints.

**Application Logic**
[`GroupService.java`](../src/main/java/dev/pulsermm/enrolment/application/GroupService.java) & [`MoveEndpointService.java`](../src/main/java/dev/pulsermm/enrolment/application/MoveEndpointService.java):
	- `createGroup` & `moveEndpoint` - Execute the business rules ensuring groups maintain a valid tree structure without cyclical dependencies, and handle the safe migration of an endpoint from one node to another.
[`TagService.java`](../src/main/java/dev/pulsermm/enrolment/application/TagService.java) & [`TagRuleService.java`](../src/main/java/dev/pulsermm/enrolment/application/TagRuleService.java):
	- `setEndpointTags` & `evaluateRules` - Manage the direct assignment of static labels to machines, while concurrently evaluating complex logical constraints (like operating system versions) to automatically apply or strip dynamic tags.

**Domain Entities**
[`Group.java`](../src/main/java/dev/pulsermm/enrolment/domain/Group.java):
	- Represents a rigid, hierarchical folder directory (like "Servers" or "Workstations") that dictates broad policy application and administrative scoping.
[`TagRule.java`](../src/main/java/dev/pulsermm/enrolment/domain/TagRule.java):
	- Defines dynamic, logical criteria that, when matched against an endpoint's current state or telemetry, automatically assigns a specific label to that machine.
[`EndpointTag.java`](../src/main/java/dev/pulsermm/enrolment/domain/EndpointTag.java) & [`EndpointTagId.java`](../src/main/java/dev/pulsermm/enrolment/domain/EndpointTagId.java):
	- Represent the many-to-many relationship linking a specific endpoint UUID to a descriptive string label, utilizing a composite database key to ensure uniqueness.

**Infrastructure Repositories**
[`GroupRepository.java`](../src/main/java/dev/pulsermm/enrolment/infrastructure/GroupRepository.java), [`TagRuleRepository.java`](../src/main/java/dev/pulsermm/enrolment/infrastructure/TagRuleRepository.java), & [`EndpointTagRepository.java`](../src/main/java/dev/pulsermm/enrolment/infrastructure/EndpointTagRepository.java):
	- Provide optimized Spring Data queries to persist the organizational tree, retrieve active dynamic rules, and perform batch updates on endpoint labels.

### description
Managing thousands of endpoints requires robust and layered organizational tools. The Enrolment Service solves this by providing two distinct mechanisms: rigid Groups and flexible Tags. Groups function like traditional folders; when an administrator utilizes the `GroupController` to create a `Group`, they establish a definitive hierarchy. The `MoveEndpointService` guarantees an endpoint resides in exactly one group at a time, establishing a clear boundary ideal for broad Role-Based Access Control enforcement. 

Conversely, Tags provide a fluid, overlapping labeling system. While administrators can assign static tags manually using the `TagService`, the system derives immense power from dynamic `TagRule` evaluation. When the `TagRuleController` accepts a new `CreateTagRuleRequest`, the `TagRuleService` stores the logical constraints. The system can subsequently scan endpoint telemetry and execute the `evaluateRules` logic. If a server suddenly reports its disk space dropping below a critical threshold, a pre-defined rule can automatically insert an `EndpointTag` entity, instantly attaching an "Urgent Maintenance" label to the machine. This dual approach allows the web application to provide complex filtering, ensuring IT staff can instantly isolate specific subsets of the fleet based on both their permanent location and current operational state.
