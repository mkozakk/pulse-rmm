import { useMemo, useState } from 'react'
import AppShell from '../components/AppShell'
import {
  useAckScriptExecutionMutation,
  useApproveScriptMutation,
  useCreateScriptMutation,
  useGetEndpointsQuery,
  useGetScriptRunResultsQuery,
  useGetScriptsQuery,
  useRunScriptMutation
} from '../api/pulseApi'

function ScriptEditor({ onCreate, saving }) {
  const [name, setName] = useState('')
  const [body, setBody] = useState('')

  async function handleCreate() {
    await onCreate({ name, body })
    setName('')
    setBody('')
  }

  return (
    <section className="panel-card stack">
      <h2 className="section-title">Upload script</h2>
      <div className="stack">
        <input value={name} onChange={e => setName(e.target.value)} placeholder="Script name" />
        <textarea value={body} onChange={e => setBody(e.target.value)} placeholder="Write script body here" rows={8} />
        <button onClick={handleCreate} disabled={saving}>Create script</button>
      </div>
    </section>
  )
}

export default function ScriptsPage() {
  const scripts = useGetScriptsQuery({ status: 'all' }, { pollingInterval: 30000 })
  const endpoints = useGetEndpointsQuery(undefined, { pollingInterval: 30000 })
  const [createScript] = useCreateScriptMutation()
  const [approveScript] = useApproveScriptMutation()
  const [runScript] = useRunScriptMutation()
  const [ackExecution] = useAckScriptExecutionMutation()
  const [selectedScriptId, setSelectedScriptId] = useState('')
  const [selectedRunId, setSelectedRunId] = useState('')
  const [selectedEndpoints, setSelectedEndpoints] = useState([])
  const [pendingAck, setPendingAck] = useState({ runId: '', endpointId: '' })
  const [busy, setBusy] = useState('')

  const scriptList = scripts.data?.scripts ?? []
  const endpointList = endpoints.data ?? []
  const selectedRun = useGetScriptRunResultsQuery(selectedRunId, { skip: !selectedRunId, pollingInterval: selectedRunId ? 30000 : 0 })
  const selectedScript = useMemo(() => scriptList.find(script => script.id === selectedScriptId) ?? null, [scriptList, selectedScriptId])

  async function handleCreateScript(payload) {
    setBusy('create')
    try {
      await createScript(payload).unwrap()
    } finally {
      setBusy('')
    }
  }

  async function handleApprove(id) {
    setBusy(id)
    try {
      await approveScript(id).unwrap()
    } finally {
      setBusy('')
    }
  }

  async function handleRun() {
    if (!selectedScriptId || selectedEndpoints.length === 0) return
    setBusy('run')
    try {
      const result = await runScript({ id: selectedScriptId, endpointIds: selectedEndpoints, secrets: {} }).unwrap()
      setSelectedRunId(result.runId)
    } finally {
      setBusy('')
    }
  }

  async function handleAck() {
    if (!pendingAck.runId || !pendingAck.endpointId) return
    setBusy('ack')
    try {
      await ackExecution({ ...pendingAck, exitCode: 0, output: '' }).unwrap()
      setPendingAck({ runId: '', endpointId: '' })
    } finally {
      setBusy('')
    }
  }

  function toggleEndpoint(id) {
    setSelectedEndpoints(prev => prev.includes(id) ? prev.filter(item => item !== id) : [...prev, id])
  }

  return (
    <AppShell title="Scripts" subtitle="Library, approval flow, bulk runs, and execution results.">
      <div className="scripts-grid">
        <ScriptEditor onCreate={handleCreateScript} saving={busy === 'create'} />

        <section className="panel-card stack">
          <h2 className="section-title">Library / pending scripts</h2>
          <div className="list-card">
            {scriptList.map(script => (
              <div key={script.id} className="list-row scripts-row">
                <div>
                  <strong>{script.name}</strong>
                  <div className="muted">{script.approved ? 'approved' : 'pending approval'}</div>
                </div>
                <div className="inline-actions">
                  <button onClick={() => setSelectedScriptId(script.id)}>Select</button>
                  {!script.approved && <button onClick={() => handleApprove(script.id)} disabled={busy === script.id}>Approve</button>}
                </div>
              </div>
            ))}
          </div>
          {selectedScript && <p className="panel-empty">Selected: {selectedScript.name}</p>}
        </section>

        <section className="panel-card stack">
          <h2 className="section-title">Run script</h2>
          <div className="list-card">
            {endpointList.map(endpoint => (
              <label key={endpoint.id} className="list-row scripts-row checkbox-row">
                <span>{endpoint.hostname}</span>
                <input type="checkbox" checked={selectedEndpoints.includes(endpoint.id)} onChange={() => toggleEndpoint(endpoint.id)} />
              </label>
            ))}
          </div>
          <button onClick={handleRun} disabled={busy === 'run' || !selectedScriptId}>Run on selected endpoints</button>
        </section>

        <section className="panel-card stack">
          <h2 className="section-title">Run results</h2>
          <div className="stack">
            <input value={selectedRunId} onChange={e => setSelectedRunId(e.target.value)} placeholder="Run ID" />
            <div className="list-card">
              {(selectedRun.data?.results ?? []).map(result => (
                <div key={result.endpointId} className="list-row scripts-row">
                  <div>
                    <strong>{result.endpointId}</strong>
                    <div className="muted">{result.pending ? 'pending' : `exit ${result.exitCode ?? 'n/a'}`}</div>
                  </div>
                  <button onClick={() => setPendingAck({ runId: selectedRunId, endpointId: result.endpointId })}>Ack</button>
                </div>
              ))}
            </div>
            <button onClick={handleAck} disabled={busy === 'ack' || !pendingAck.runId}>Send ack</button>
          </div>
        </section>
      </div>
    </AppShell>
  )
}
