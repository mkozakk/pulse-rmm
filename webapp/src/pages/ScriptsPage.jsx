import { useMemo, useState } from 'react'
import { FilePlus, ShieldCheck, Play, CheckCheck } from 'lucide-react'
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
      <div className="form-field">
        <label className="field-label">Script name</label>
        <input value={name} onChange={e => setName(e.target.value)} placeholder="e.g. cleanup-logs.sh" />
      </div>
      <div className="form-field">
        <label className="field-label">Body</label>
        <textarea value={body} onChange={e => setBody(e.target.value)} placeholder="Write script body here" rows={8} />
      </div>
      <div className="form-actions">
        <button className="icon-btn btn-primary" onClick={handleCreate} disabled={saving}>
          <FilePlus size={14} />Create script
        </button>
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
  const selectedScript = useMemo(() => scriptList.find(s => s.id === selectedScriptId) ?? null, [scriptList, selectedScriptId])

  async function handleCreateScript(payload) {
    setBusy('create')
    try { await createScript(payload).unwrap() }
    finally { setBusy('') }
  }

  async function handleApprove(id) {
    setBusy(id)
    try { await approveScript(id).unwrap() }
    finally { setBusy('') }
  }

  async function handleRun() {
    if (!selectedScriptId || selectedEndpoints.length === 0) return
    setBusy('run')
    try {
      const result = await runScript({ id: selectedScriptId, endpointIds: selectedEndpoints, secrets: {} }).unwrap()
      setSelectedRunId(result.runId)
    } finally { setBusy('') }
  }

  async function handleAck() {
    if (!pendingAck.runId || !pendingAck.endpointId) return
    setBusy('ack')
    try {
      await ackExecution({ ...pendingAck, exitCode: 0, output: '' }).unwrap()
      setPendingAck({ runId: '', endpointId: '' })
    } finally { setBusy('') }
  }

  function toggleEndpoint(id) {
    setSelectedEndpoints(prev => prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id])
  }

  return (
    <AppShell title="Scripts">
      <div className="scripts-grid">
        <ScriptEditor onCreate={handleCreateScript} saving={busy === 'create'} />

        <section className="panel-card stack">
          <h2 className="section-title">Library / pending scripts</h2>
          <div className="list-card">
            {scriptList.length === 0 && <p className="panel-empty">No scripts yet.</p>}
            {scriptList.map(script => (
              <div
                key={script.id}
                className={`list-row scripts-row${script.id === selectedScriptId ? ' selected-row' : ''}`}
                style={{ cursor: 'pointer', borderRadius: 8 }}
                onClick={() => setSelectedScriptId(script.id)}
              >
                <div>
                  <strong>{script.name}</strong>
                  <div className="muted" style={{ fontSize: 12, marginTop: 2 }}>
                    {script.approved
                      ? <span className="badge badge-green">approved</span>
                      : <span className="badge badge-gray">pending approval</span>
                    }
                  </div>
                </div>
                <div className="inline-actions" onClick={e => e.stopPropagation()}>
                  {!script.approved && (
                    <button className="btn-sm icon-btn" onClick={() => handleApprove(script.id)} disabled={busy === script.id}>
                      <ShieldCheck size={13} />Approve
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
          {selectedScript && (
            <p className="panel-empty" style={{ fontSize: 12 }}>
              Selected: <strong>{selectedScript.name}</strong>
            </p>
          )}
        </section>

        <section className="panel-card stack">
          <h2 className="section-title">Run on endpoints</h2>
          {!selectedScript
            ? <p className="panel-empty">Select a script from the library first.</p>
            : (
              <p style={{ fontSize: 13 }}>
                Running: <strong>{selectedScript.name}</strong>
                {!selectedScript.approved && <span className="badge badge-gray" style={{ marginLeft: 6 }}>not approved</span>}
              </p>
            )
          }
          <div className="list-card">
            {endpointList.map(ep => (
              <label key={ep.id} className="list-row scripts-row checkbox-row" style={{ cursor: 'pointer' }}>
                <span>{ep.hostname}</span>
                <input type="checkbox" checked={selectedEndpoints.includes(ep.id)} onChange={() => toggleEndpoint(ep.id)} />
              </label>
            ))}
          </div>
          <div className="form-actions">
            <button className="icon-btn btn-primary" onClick={handleRun} disabled={busy === 'run' || !selectedScriptId || selectedEndpoints.length === 0}>
              <Play size={13} />Run on selected ({selectedEndpoints.length})
            </button>
          </div>
        </section>

        <section className="panel-card stack">
          <h2 className="section-title">Run results</h2>
          {!selectedRunId
            ? <p className="panel-empty">Run a script to see results here.</p>
            : (
              <>
                <p className="muted" style={{ fontSize: 12 }}>Run <code style={{ fontFamily: 'monospace' }}>{selectedRunId}</code></p>
                <div className="list-card">
                  {(selectedRun.data?.results ?? []).map(result => (
                    <div key={result.endpointId} className="list-row scripts-row">
                      <div>
                        <strong>{result.endpointId}</strong>
                        <div className="muted" style={{ fontSize: 12, marginTop: 2 }}>
                          {result.pending
                            ? <span className="badge badge-gray">pending</span>
                            : <span className="badge badge-green">exit {result.exitCode ?? 'n/a'}</span>
                          }
                        </div>
                      </div>
                      <button className="btn-sm" onClick={() => setPendingAck({ runId: selectedRunId, endpointId: result.endpointId })}>Ack</button>
                    </div>
                  ))}
                </div>
                <div className="form-actions">
                  <button className="icon-btn btn-primary" onClick={handleAck} disabled={busy === 'ack' || !pendingAck.runId}>
                    <CheckCheck size={13} />Send ack
                  </button>
                </div>
              </>
            )
          }
        </section>
      </div>
    </AppShell>
  )
}
