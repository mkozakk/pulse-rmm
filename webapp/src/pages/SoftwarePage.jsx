import { useMemo, useState } from 'react'
import { Trash2 } from 'lucide-react'
import AppShell from '../components/AppShell'
import {
  useGetEndpointsQuery,
  useGetSoftwareQuery,
  useRemoveSoftwareMutation
} from '../api/pulseApi'

export default function SoftwarePage() {
  const endpoints = useGetEndpointsQuery(undefined, { pollingInterval: 30000 })
  const [selectedEndpointId, setSelectedEndpointId] = useState('')
  const software = useGetSoftwareQuery(selectedEndpointId, { skip: !selectedEndpointId, pollingInterval: selectedEndpointId ? 30000 : 0 })
  const [removeSoftware] = useRemoveSoftwareMutation()
  const [busy, setBusy] = useState('')

  const endpointList = endpoints.data ?? []
  const items = software.data ?? []
  const selectedEndpoint = useMemo(() => endpointList.find(ep => ep.id === selectedEndpointId) ?? null, [endpointList, selectedEndpointId])

  async function handleRemove(name, id) {
    if (!selectedEndpointId) return
    setBusy(name)
    try { await removeSoftware({ endpointId: selectedEndpointId, name, id }).unwrap() }
    finally { setBusy('') }
  }

  return (
    <AppShell title="Software">
      <div className="stack">
        <section className="panel-card stack">
          <h2 className="section-title">Endpoint</h2>
          <div className="form-field" style={{ maxWidth: 360 }}>
            <label className="field-label">Select endpoint</label>
            <select value={selectedEndpointId} onChange={e => setSelectedEndpointId(e.target.value)}>
              <option value="">Choose an endpoint</option>
              {endpointList.map(ep => (
                <option key={ep.id} value={ep.id}>{ep.hostname}</option>
              ))}
            </select>
          </div>
          {selectedEndpoint && (
            <p className="panel-empty">{selectedEndpoint.hostname} - {selectedEndpoint.status}</p>
          )}
        </section>

        <section className="panel-card stack">
          <h2 className="section-title">Installed software</h2>
          {items.length === 0
            ? <p className="panel-empty">{selectedEndpointId ? 'No software inventory yet.' : 'Select an endpoint above.'}</p>
            : (
              <table className="enrolment-table">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Version</th>
                    <th>Source</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {items.map(item => (
                    <tr key={`${item.name}-${item.version}`}>
                      <td style={{ fontWeight: 500 }}>{item.name}</td>
                      <td className="col-muted">{item.version}</td>
                      <td className="col-muted">{item.source}</td>
                      <td className="col-right">
                        <button
                          className="icon-btn endpoint-action proc-kill-btn"
                          disabled={busy === item.name}
                          onClick={() => handleRemove(item.name, item.id)}
                        >
                          <Trash2 size={12} />Remove
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )
          }
        </section>
      </div>
    </AppShell>
  )
}
