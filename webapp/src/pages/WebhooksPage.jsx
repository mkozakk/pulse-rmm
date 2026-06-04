import { useState, Fragment } from 'react'
import { useNavigate } from 'react-router-dom'
import { Plus, Pencil, History, Trash2 } from 'lucide-react'
import AppShell from '../components/AppShell'
import {
  useListWebhooksQuery,
  useCreateWebhookMutation,
  useUpdateWebhookMutation,
  useDeleteWebhookMutation
} from '../api/pulseApi'

const EVENT_TYPE_OPTIONS = [
  'alert.fired',
  'alert.acknowledged',
  'endpoint.enrolled',
  'endpoint.online',
  'endpoint.offline',
  'audit.*'
]

const EMPTY_FORM = {
  url: '', eventTypes: [], secret: '', enabled: true
}

function WebhookForm({ initial = EMPTY_FORM, onSubmit, onCancel, submitLabel }) {
  const [form, setForm] = useState(initial)
  const [error, setError] = useState('')

  function toggleEventType(type) {
    setForm(f => ({
      ...f,
      eventTypes: f.eventTypes.includes(type)
        ? f.eventTypes.filter(t => t !== type)
        : [...f.eventTypes, type]
    }))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    if (!form.url.startsWith('https://') && !form.url.startsWith('http://')) {
      setError('URL must start with https://')
      return
    }
    if (form.eventTypes.length === 0) {
      setError('Select at least one event type')
      return
    }
    setError('')
    await onSubmit(form)
  }

  return (
    <form onSubmit={handleSubmit} className="stack">
      {error && <p className="form-error">{error}</p>}
      <div className="form-row">
        <div className="form-field" style={{ flex: 2 }}>
          <label className="field-label">URL</label>
          <input
            placeholder="URL (https://...)"
            value={form.url}
            onChange={e => setForm(f => ({ ...f, url: e.target.value }))}
            required
          />
        </div>
        <div className="form-field" style={{ flex: 1 }}>
          <label className="field-label">Secret</label>
          <input
            type="password"
            placeholder="Secret (min 16 chars)"
            value={form.secret}
            onChange={e => setForm(f => ({ ...f, secret: e.target.value }))}
            minLength={16}
            required={submitLabel === 'Add Webhook'}
          />
        </div>
      </div>
      <div className="form-field">
        <label className="field-label">Event types</label>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem', paddingTop: '0.25rem' }}>
          {EVENT_TYPE_OPTIONS.map(type => (
            <label key={type} style={{ display: 'flex', alignItems: 'center', gap: '0.25rem', fontSize: '0.875rem', cursor: 'pointer' }}>
              <input
                type="checkbox"
                checked={form.eventTypes.includes(type)}
                onChange={() => toggleEventType(type)}
              />
              {type}
            </label>
          ))}
        </div>
      </div>
      <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.875rem' }}>
        <input
          type="checkbox"
          checked={form.enabled}
          onChange={e => setForm(f => ({ ...f, enabled: e.target.checked }))}
        />
        Enabled
      </label>
      <div className="form-actions">
        <button type="submit" className="icon-btn btn-primary">{submitLabel}</button>
        <button type="button" className="icon-btn endpoint-action" onClick={onCancel}>Cancel</button>
      </div>
    </form>
  )
}

export default function WebhooksPage() {
  const navigate = useNavigate()
  const { data: webhooks = [] } = useListWebhooksQuery()
  const [createWebhook] = useCreateWebhookMutation()
  const [updateWebhook] = useUpdateWebhookMutation()
  const [deleteWebhook] = useDeleteWebhookMutation()

  const [showAdd, setShowAdd] = useState(false)
  const [editId, setEditId] = useState(null)

  async function handleCreate(form) {
    await createWebhook({ url: form.url, eventTypes: form.eventTypes, secret: form.secret, enabled: form.enabled })
    setShowAdd(false)
  }

  async function handleUpdate(id, form) {
    const body = { url: form.url, eventTypes: form.eventTypes, enabled: form.enabled }
    if (form.secret) body.secret = form.secret
    await updateWebhook({ id, ...body })
    setEditId(null)
  }

  async function handleDelete(id) {
    if (!window.confirm('Delete this webhook?')) return
    await deleteWebhook(id)
  }

  return (
    <AppShell
      title="Webhooks"
      actions={
        !showAdd && (
          <button className="icon-btn endpoint-action" onClick={() => setShowAdd(true)}>
            <Plus size={14} />Add Webhook
          </button>
        )
      }
    >
      <div className="stack">
        {showAdd && (
          <section className="panel-card stack">
            <h2 className="section-title">Add Webhook</h2>
            <WebhookForm
              submitLabel="Add Webhook"
              onSubmit={handleCreate}
              onCancel={() => setShowAdd(false)}
            />
          </section>
        )}

        <section className="panel-card stack">
          <h2 className="section-title">Registered Webhooks ({webhooks.length})</h2>
          {webhooks.length === 0
            ? <p className="panel-empty">No webhooks registered.</p>
            : (
              <table className="enrolment-table">
                <thead>
                  <tr>
                    <th>URL</th>
                    <th>Events</th>
                    <th>Status</th>
                    <th>Created</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {webhooks.map(wh => (
                    <Fragment key={wh.id}>
                      <tr>
                        <td style={{ fontFamily: 'monospace', fontSize: 12, wordBreak: 'break-all' }}>{wh.url}</td>
                        <td className="col-muted" style={{ fontSize: 12 }}>{wh.eventTypes.join(', ')}</td>
                        <td><span className={`badge badge-${wh.enabled ? 'green' : 'gray'}`}>{wh.enabled ? 'enabled' : 'disabled'}</span></td>
                        <td className="col-muted" style={{ fontSize: 12 }}>{new Date(wh.createdAt).toLocaleDateString()}</td>
                        <td className="col-right">
                          <span style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
                            <button className="icon-btn endpoint-action" onClick={() => navigate(`/webhooks/${wh.id}`)}><History size={13} />History</button>
                            <button className="icon-btn endpoint-action" onClick={() => setEditId(editId === wh.id ? null : wh.id)}><Pencil size={13} />Edit</button>
                            <button className="icon-btn endpoint-action proc-kill-btn" onClick={() => handleDelete(wh.id)}><Trash2 size={13} />Delete</button>
                          </span>
                        </td>
                      </tr>
                      {editId === wh.id && (
                        <tr>
                          <td colSpan={5} style={{ padding: '0.75rem' }}>
                            <WebhookForm
                              initial={{ url: wh.url, eventTypes: wh.eventTypes, secret: '', enabled: wh.enabled }}
                              submitLabel="Save"
                              onSubmit={form => handleUpdate(wh.id, form)}
                              onCancel={() => setEditId(null)}
                            />
                          </td>
                        </tr>
                      )}
                    </Fragment>
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
