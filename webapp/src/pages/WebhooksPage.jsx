import { useState } from 'react'
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
    <form onSubmit={handleSubmit} className="stack" style={{ marginTop: '0.75rem' }}>
      {error && <p style={{ color: '#dc2626', fontSize: '0.875rem' }}>{error}</p>}
      <div className="form-grid">
        <input
          placeholder="URL (https://...)"
          value={form.url}
          onChange={e => setForm(f => ({ ...f, url: e.target.value }))}
          required
        />
        <input
          type="password"
          placeholder="Secret (min 16 chars)"
          value={form.secret}
          onChange={e => setForm(f => ({ ...f, secret: e.target.value }))}
          minLength={16}
          required={submitLabel === 'Add Webhook'}
        />
      </div>
      <div>
        <p style={{ fontSize: '0.875rem', marginBottom: '0.4rem' }}>Event types</p>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem' }}>
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
      <div style={{ display: 'flex', gap: '0.5rem' }}>
        <button type="submit">{submitLabel}</button>
        <button type="button" onClick={onCancel}>Cancel</button>
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
    <AppShell title="Webhooks" subtitle="Outbound event notifications to external systems">
      <div className="stack">
        {showAdd ? (
          <div className="panel-card">
            <p className="section-title">Add Webhook</p>
            <WebhookForm
              submitLabel="Add Webhook"
              onSubmit={handleCreate}
              onCancel={() => setShowAdd(false)}
            />
          </div>
        ) : (
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <button className="icon-btn" onClick={() => setShowAdd(true)}><Plus size={14} />Add Webhook</button>
          </div>
        )}

        <div className="panel-card">
          <p className="section-title">Registered Webhooks</p>
          {webhooks.length === 0 ? (
            <p className="panel-empty" style={{ marginTop: '0.5rem' }}>No webhooks registered.</p>
          ) : (
            <div className="list-card" style={{ marginTop: '0.5rem' }}>
              {webhooks.map(wh => (
                <div key={wh.id}>
                  {editId === wh.id ? (
                    <div style={{ padding: '0.75rem 0' }}>
                      <WebhookForm
                        initial={{ url: wh.url, eventTypes: wh.eventTypes, secret: '', enabled: wh.enabled }}
                        submitLabel="Save"
                        onSubmit={form => handleUpdate(wh.id, form)}
                        onCancel={() => setEditId(null)}
                      />
                    </div>
                  ) : (
                    <div className="list-row" style={{ justifyContent: 'space-between', alignItems: 'flex-start' }}>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ fontFamily: 'monospace', fontSize: '0.875rem', wordBreak: 'break-all' }}>
                          {wh.url}
                        </div>
                        <div style={{ fontSize: '0.8rem', color: '#6b7280', marginTop: '0.25rem' }}>
                          {wh.eventTypes.join(', ')} · {wh.enabled ? 'enabled' : 'disabled'} · created {new Date(wh.createdAt).toLocaleDateString()}
                        </div>
                      </div>
                      <div style={{ display: 'flex', gap: '0.5rem', flexShrink: 0, marginLeft: '1rem' }}>
                        <button className="icon-btn" onClick={() => navigate(`/webhooks/${wh.id}`)}><History size={13} />History</button>
                        <button className="icon-btn" onClick={() => setEditId(wh.id)}><Pencil size={13} />Edit</button>
                        <button className="icon-btn" onClick={() => handleDelete(wh.id)}><Trash2 size={13} />Delete</button>
                      </div>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </AppShell>
  )
}
