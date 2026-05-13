import { useState } from 'react'
import AppShell from '../components/AppShell'
import { useGetAlertRulesQuery, useCreateAlertRuleMutation, useDeleteAlertRuleMutation } from '../api/pulseApi'

export default function AlertsPage() {
  const { data: rules = [], refetch } = useGetAlertRulesQuery()
  const [createRule] = useCreateAlertRuleMutation()
  const [deleteRule] = useDeleteAlertRuleMutation()
  const [form, setForm] = useState({
    name: '', metricType: 'cpu', operator: '>', threshold: 90,
    durationSecs: 300, targetType: 'group', targetValue: ''
  })

  function set(field) {
    return e => setForm(f => ({ ...f, [field]: e.target.value }))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    await createRule({
      name: form.name,
      metricType: form.metricType,
      operator: form.operator,
      threshold: Number(form.threshold),
      durationSecs: Number(form.durationSecs),
      target: { type: form.targetType, value: form.targetValue }
    })
    setForm(f => ({ ...f, name: '', targetValue: '' }))
    refetch()
  }

  async function handleDelete(id) {
    await deleteRule(id)
    refetch()
  }

  return (
    <AppShell title="Alerts" subtitle="Threshold-based alerting rules">
      <div className="stack">
        <div className="panel-card">
          <p className="section-title">New Rule</p>
          <form onSubmit={handleSubmit} style={{ marginTop: '0.75rem' }}>
            <div className="form-grid alerts-form">
              <input placeholder="Name" value={form.name} onChange={set('name')} required />
              <select value={form.metricType} onChange={set('metricType')}>
                <option value="cpu">CPU</option>
                <option value="ram">RAM</option>
                <option value="disk">Disk</option>
              </select>
              <select value={form.operator} onChange={set('operator')}>
                <option value=">">&gt; (above)</option>
                <option value="<">&lt; (below)</option>
              </select>
              <input type="number" placeholder="Threshold %" value={form.threshold}
                min={0} max={100} onChange={set('threshold')} required />
              <input type="number" placeholder="Duration (s)" value={form.durationSecs}
                min={30} max={3600} onChange={set('durationSecs')} required />
              <select value={form.targetType} onChange={set('targetType')}>
                <option value="group">Group</option>
                <option value="tag">Tag</option>
              </select>
              <input
                placeholder={form.targetType === 'tag' ? 'key=value' : 'Group UUID'}
                value={form.targetValue}
                onChange={set('targetValue')}
                required
              />
              <button type="submit">Create</button>
            </div>
          </form>
        </div>

        <div className="panel-card">
          <p className="section-title">Rules</p>
          {rules.length === 0
            ? <p className="panel-empty" style={{ marginTop: '0.5rem' }}>No rules defined.</p>
            : (
              <div className="list-card" style={{ marginTop: '0.5rem' }}>
                {rules.map(r => (
                  <div key={r.id} className="list-row">
                    <div>
                      <p>{r.name}</p>
                      <p className="muted">{r.metricType} {r.operator} {r.threshold}% for {r.durationSecs}s — {r.targetType}: {r.targetValue}</p>
                    </div>
                    <button onClick={() => handleDelete(r.id)}>Delete</button>
                  </div>
                ))}
              </div>
            )
          }
        </div>
      </div>
    </AppShell>
  )
}
