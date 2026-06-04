import { useState } from 'react'
import { Plus, Trash2 } from 'lucide-react'
import AppShell from '../components/AppShell'
import { useGetAlertRulesQuery, useCreateAlertRuleMutation, useDeleteAlertRuleMutation, useGetGroupsQuery } from '../api/pulseApi'

export default function AlertsPage() {
  const { data: rules = [] } = useGetAlertRulesQuery()
  const { data: groups = [] } = useGetGroupsQuery()
  const [createRule] = useCreateAlertRuleMutation()
  const [deleteRule] = useDeleteAlertRuleMutation()
  const [form, setForm] = useState({
    name: '', metricType: 'cpu', operator: '>', threshold: 90,
    durationSecs: 300, targetType: 'group', targetValue: ''
  })

  function set(field) {
    return e => setForm(f => ({ ...f, [field]: e.target.value }))
  }

  function setTargetType(e) {
    setForm(f => ({ ...f, targetType: e.target.value, targetValue: '' }))
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
  }

  return (
    <AppShell title="Alerts">
      <div className="stack">
        <section className="panel-card stack">
          <h2 className="section-title">New rule</h2>
          <form onSubmit={handleSubmit} className="stack">
            <div className="form-row">
              <div className="form-field">
                <label className="field-label">Name</label>
                <input placeholder="e.g. High CPU" value={form.name} onChange={set('name')} required />
              </div>
              <div className="form-field">
                <label className="field-label">Metric</label>
                <select value={form.metricType} onChange={set('metricType')}>
                  <option value="cpu">CPU</option>
                  <option value="ram">RAM</option>
                  <option value="disk">Disk</option>
                </select>
              </div>
              <div className="form-field">
                <label className="field-label">Operator</label>
                <select value={form.operator} onChange={set('operator')}>
                  <option value=">">&gt; above</option>
                  <option value="<">&lt; below</option>
                </select>
              </div>
              <div className="form-field">
                <label className="field-label">Threshold %</label>
                <input type="number" placeholder="90" value={form.threshold} min={0} max={100} onChange={set('threshold')} required />
              </div>
              <div className="form-field">
                <label className="field-label">Duration (s)</label>
                <input type="number" placeholder="300" value={form.durationSecs} min={30} max={3600} onChange={set('durationSecs')} required />
              </div>
              <div className="form-field">
                <label className="field-label">Target type</label>
                <select value={form.targetType} onChange={setTargetType}>
                  <option value="group">Group</option>
                  <option value="tag">Tag</option>
                </select>
              </div>
              <div className="form-field">
                <label className="field-label">{form.targetType === 'tag' ? 'Tag (key=value)' : 'Group'}</label>
                {form.targetType === 'group'
                  ? (
                    <select value={form.targetValue} onChange={set('targetValue')} required>
                      <option value="">Select group</option>
                      {groups.map(g => <option key={g.id} value={g.id}>{g.name}</option>)}
                    </select>
                  )
                  : <input placeholder="env=prod" value={form.targetValue} onChange={set('targetValue')} required />
                }
              </div>
            </div>
            <div className="form-actions">
              <button type="submit" className="icon-btn btn-primary"><Plus size={14} />Create rule</button>
            </div>
          </form>
        </section>

        <section className="panel-card stack">
          <h2 className="section-title">Rules ({rules.length})</h2>
          {rules.length === 0
            ? <p className="panel-empty">No rules defined.</p>
            : (
              <table className="enrolment-table">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Condition</th>
                    <th>Target</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {rules.map(r => (
                    <tr key={r.id}>
                      <td style={{ fontWeight: 500 }}>{r.name}</td>
                      <td className="col-muted" style={{ fontFamily: 'monospace', fontSize: 12 }}>
                        {r.metricType} {r.operator} {r.threshold}% for {r.durationSecs}s
                      </td>
                      <td className="col-muted">{r.targetType}: {r.targetValue}</td>
                      <td className="col-right">
                        <button className="icon-btn endpoint-action proc-kill-btn" onClick={() => deleteRule(r.id)}>
                          <Trash2 size={12} />Delete
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
