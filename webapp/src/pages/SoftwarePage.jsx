import { useMemo, useState } from 'react'
import AppShell from '../components/AppShell'
import {
  useGetEndpointsQuery,
  useGetSoftwareQuery,
  useInstallSoftwareMutation,
  useRemoveSoftwareMutation,
  useUpdateSoftwareMutation
} from '../api/pulseApi'

function CommandForm({ label, onSubmit, busy, fields }) {
  const [values, setValues] = useState(fields)

  async function handleSubmit() {
    await onSubmit(values)
    setValues(fields)
  }

  return (
    <div className="stack">
      <div className="form-grid">
        {Object.entries(values).map(([key, value]) => (
          <input
            key={key}
            value={value}
            onChange={e => setValues(prev => ({ ...prev, [key]: e.target.value }))}
            placeholder={key}
          />
        ))}
        <button onClick={handleSubmit} disabled={busy}>{label}</button>
      </div>
    </div>
  )
}

export default function SoftwarePage() {
  const endpoints = useGetEndpointsQuery(undefined, { pollingInterval: 30000 })
  const [selectedEndpointId, setSelectedEndpointId] = useState('')
  const software = useGetSoftwareQuery(selectedEndpointId, { skip: !selectedEndpointId, pollingInterval: selectedEndpointId ? 30000 : 0 })
  const [installSoftware] = useInstallSoftwareMutation()
  const [updateSoftware] = useUpdateSoftwareMutation()
  const [removeSoftware] = useRemoveSoftwareMutation()
  const [busy, setBusy] = useState('')

  const endpointList = endpoints.data ?? []
  const items = software.data ?? []
  const selectedEndpoint = useMemo(() => endpointList.find(item => item.id === selectedEndpointId) ?? null, [endpointList, selectedEndpointId])

  async function handleInstall(values) {
    if (!selectedEndpointId) return
    setBusy('install')
    try {
      await installSoftware({ endpointId: selectedEndpointId, ...values }).unwrap()
    } finally {
      setBusy('')
    }
  }

  async function handleUpdate(values) {
    if (!selectedEndpointId) return
    setBusy('update')
    try {
      await updateSoftware({ endpointId: selectedEndpointId, ...values }).unwrap()
    } finally {
      setBusy('')
    }
  }

  async function handleRemove(values) {
    if (!selectedEndpointId) return
    setBusy('remove')
    try {
      await removeSoftware({ endpointId: selectedEndpointId, ...values }).unwrap()
    } finally {
      setBusy('')
    }
  }

  return (
    <AppShell title="Software" subtitle="Inventory and install/update/remove commands per endpoint.">
      <section className="panel-card stack">
        <h2 className="section-title">Endpoint</h2>
        <select value={selectedEndpointId} onChange={e => setSelectedEndpointId(e.target.value)}>
          <option value="">Select endpoint</option>
          {endpointList.map(endpoint => (
            <option key={endpoint.id} value={endpoint.id}>{endpoint.hostname}</option>
          ))}
        </select>
        {selectedEndpoint && <p className="panel-empty">{selectedEndpoint.hostname} · {selectedEndpoint.status}</p>}
      </section>

      <section className="panel-card stack">
        <h2 className="section-title">Installed software</h2>
        <div className="list-card">
          {items.map(item => (
            <div key={`${item.name}-${item.version}`} className="list-row">
              <span>{item.name}</span>
              <span className="muted">{item.version} · {item.source}</span>
            </div>
          ))}
        </div>
      </section>

      <section className="panel-card stack">
        <h2 className="section-title">Commands</h2>
        <CommandForm
          label="Install"
          busy={busy === 'install' || !selectedEndpointId}
          fields={{ name: '', version: '' }}
          onSubmit={handleInstall}
        />
        <CommandForm
          label="Update"
          busy={busy === 'update' || !selectedEndpointId}
          fields={{ name: '', version: '' }}
          onSubmit={handleUpdate}
        />
        <CommandForm
          label="Remove"
          busy={busy === 'remove' || !selectedEndpointId}
          fields={{ name: '' }}
          onSubmit={handleRemove}
        />
      </section>
    </AppShell>
  )
}
