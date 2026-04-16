# Enrolment & Agent Versions (`src/pages/EnrolmentPage.jsx`, `src/pages/AgentVersionsPage.jsx`)

Onboarding new endpoints — tokens, groups, and tag rules — and distributing the agent builds those endpoints install.

### code
[`pages/EnrolmentPage.jsx`](../src/pages/EnrolmentPage.jsx):
	- `EnrolmentPage` - The onboarding hub. Mints enrolment tokens (`useCreateEnrolmentTokenMutation`), creates groups (`useCreateGroupMutation`), defines and runs tag rules (`useCreateTagRuleMutation`, `useEvaluateTagRulesMutation`), and assigns endpoints to groups and tags (`useUpdateEndpointGroupMutation`, `useUpdateEndpointTagsMutation`).
	- `CopyLine` - Renders a token or install one-liner with a copy-to-clipboard button and transient "copied" feedback.

[`pages/AgentVersionsPage.jsx`](../src/pages/AgentVersionsPage.jsx):
	- `AgentVersionsPage` - Lists published agent builds (`useListAgentVersionsQuery`), uploads a new one (`usePublishAgentVersionMutation`), promotes a build to current (`useSetCurrentAgentVersionMutation`), and deletes one (`useDeleteAgentVersionMutation`).
	- `inferNextVersion` - Parses existing semantic versions and suggests the next one to pre-fill the upload form.
	- Upload form - Drag-and-drop package upload sent as `multipart/form-data`.

### description
Enrolment is where an operator turns a bare machine into a managed endpoint. A token is minted and handed to the install one-liner (the `CopyLine` helper makes that copy-paste foolproof); the agent presents the token on first contact to register itself. Groups and tag rules are organisational scaffolding set up at the same time: a tag rule classifies endpoints automatically, and "evaluate" applies the rules to the current fleet on demand so the operator can see the effect immediately rather than waiting for the next sync.

Agent-version distribution is the supply side of auto-update. A build is uploaded as a package, and exactly one version is marked **current**; agents poll for the current version and update themselves toward it. The version form infers the next semantic version from what already exists so uploads stay monotonic and the operator does not have to remember the last number.
</content>
