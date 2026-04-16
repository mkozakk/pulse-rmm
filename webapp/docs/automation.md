# Scripts & Software (`src/pages/ScriptsPage.jsx`, `src/pages/SoftwarePage.jsx`)

The script library — authoring, approval, and fleet-wide execution — and per-endpoint software inventory with package actions.

### code
[`pages/ScriptsPage.jsx`](../src/pages/ScriptsPage.jsx):
	- `ScriptsPage` - Lists scripts (filterable by status, paginated) via `useGetScriptsQuery`, and composes authoring, approval, and run flows.
	- `ScriptEditor` - Inline editor that creates a script with `useCreateScriptMutation`.
	- Approval - `useApproveScriptMutation` moves a draft script to approved before it can run.
	- Execution - `useRunScriptMutation` fans a script out to selected endpoints; `useGetScriptRunResultsQuery` polls per-endpoint results, and `useAckScriptExecutionMutation` acknowledges them.

[`pages/SoftwarePage.jsx`](../src/pages/SoftwarePage.jsx):
	- `SoftwarePage` - Picks an endpoint, reads its installed software with `useGetSoftwareQuery` (polled while selected), and exposes install/update/remove actions.
	- Package actions - `useInstallSoftwareMutation`, `useUpdateSoftwareMutation`, and `useRemoveSoftwareMutation`, each scoped to the selected endpoint so only that endpoint's inventory is invalidated.

### description
Scripts move through an explicit lifecycle — draft → approved → run — and the UI enforces the gate by exposing the run controls only after approval, mirroring the backend's separation of who may write a script from who may execute it across the fleet. Execution is a fan-out: one run targets many endpoints, and results arrive per endpoint, so the page polls a results query rather than expecting a single synchronous response.

Software management is strictly endpoint-scoped. The inventory query and every package mutation key their cache tags by endpoint id, so installing on one machine refetches only that machine's list. While an endpoint is selected the inventory polls so the table reflects the outcome of an install or removal without a manual refresh.
</content>
