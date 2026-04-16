# File Browser (`src/pages/FilesPage.jsx`)

Browses, uploads to, and downloads from the endpoint filesystem through the gateway file API.

### code
[`pages/FilesPage.jsx`](../src/pages/FilesPage.jsx):
	- `FilesPage` - Lists a directory with `useListFilesQuery({ id, path })`, navigating by setting `path`; directories and files render with type icons and human-readable sizes.
	- `download` - Fetches `${API_BASE}/files/{id}/download?path=…` with an explicit `Authorization: Bearer` header, turns the response into a Blob, and triggers a browser save with the entry's name.
	- `uploadFile` (via `useUploadFileMutation`) - Posts the selected file as `multipart/form-data` to `/files/{id}/upload?path=…`.
	- `fmtSize` - Formats byte counts as B/KB/MB/GB.

### description
The file browser is a stateless view over the endpoint's filesystem: the current `path` is the only local state, and every navigation re-issues the listing query (which is uncached, so it always reflects the real directory). Uploads go through the RTK Query mutation as a multipart form so the file streams to the gateway and on to the agent.

Download is the one operation that bypasses the RTK Query hooks and uses `fetch` directly. That is deliberate — the response is a binary stream, not JSON, so the page attaches the bearer token itself, reads the body as a Blob, and synthesises an `<a download>` click to save it under the original filename. Listings and uploads still go through the shared client; only the binary download needs the manual path.
</content>
