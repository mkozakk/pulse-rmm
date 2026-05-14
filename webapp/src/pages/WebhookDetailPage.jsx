import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import AppShell from '../components/AppShell'
import {
  useListWebhooksQuery,
  useListDeliveriesQuery,
  useGetDeliveryQuery
} from '../api/pulseApi'

const STATUS_STYLES = {
  success:    { background: '#dcfce7', color: '#166534' },
  retrying:   { background: '#fef9c3', color: '#854d0e' },
  dead_letter: { background: '#fee2e2', color: '#991b1b' },
  pending:    { background: '#f3f4f6', color: '#6b7280' }
}

function StatusBadge({ status }) {
  const style = STATUS_STYLES[status] ?? STATUS_STYLES.pending
  return (
    <span className="badge" style={style}>{status}</span>
  )
}

function DeliveryPanel({ deliveryId, onClose }) {
  const { data: delivery, isLoading } = useGetDeliveryQuery(deliveryId)

  return (
    <div style={{
      position: 'fixed', top: 0, right: 0, bottom: 0, width: '420px',
      background: '#fff', borderLeft: '1px solid #e5e7eb',
      boxShadow: '-4px 0 16px rgba(0,0,0,0.08)',
      display: 'flex', flexDirection: 'column', zIndex: 100
    }}>
      <div style={{ padding: '1rem', borderBottom: '1px solid #e5e7eb', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <p style={{ margin: 0, fontWeight: 600 }}>Delivery Detail</p>
        <button onClick={onClose}>✕</button>
      </div>
      <div style={{ padding: '1rem', overflowY: 'auto', flex: 1 }}>
        {isLoading ? (
          <p className="panel-empty">Loading…</p>
        ) : delivery ? (
          <div className="stack">
            <div>
              <p style={{ fontSize: '0.8rem', color: '#6b7280' }}>Status</p>
              <StatusBadge status={delivery.status} />
            </div>
            <div>
              <p style={{ fontSize: '0.8rem', color: '#6b7280' }}>Event type</p>
              <p style={{ fontFamily: 'monospace', fontSize: '0.875rem' }}>{delivery.eventType}</p>
            </div>
            <div>
              <p style={{ fontSize: '0.8rem', color: '#6b7280' }}>Attempts / HTTP status</p>
              <p style={{ fontSize: '0.875rem' }}>{delivery.attempts} attempts · {delivery.lastStatusCode ?? '—'}</p>
            </div>
            {delivery.lastError && (
              <div>
                <p style={{ fontSize: '0.8rem', color: '#6b7280' }}>Last error</p>
                <p style={{ fontSize: '0.875rem', color: '#dc2626' }}>{delivery.lastError}</p>
              </div>
            )}
            <div>
              <p style={{ fontSize: '0.8rem', color: '#6b7280', marginBottom: '0.25rem' }}>Payload</p>
              <pre style={{
                background: '#f8fafc', border: '1px solid #e5e7eb', borderRadius: '4px',
                padding: '0.75rem', fontSize: '0.75rem', overflowX: 'auto', whiteSpace: 'pre-wrap'
              }}>
                {JSON.stringify(delivery.payload, null, 2)}
              </pre>
            </div>
          </div>
        ) : (
          <p className="panel-empty">Not found</p>
        )}
      </div>
    </div>
  )
}

export default function WebhookDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [statusFilter, setStatusFilter] = useState('')
  const [selectedDelivery, setSelectedDelivery] = useState(null)

  const { data: webhooks = [] } = useListWebhooksQuery()
  const webhook = webhooks.find(w => w.id === id)

  const { data: deliveries = [], isLoading } = useListDeliveriesQuery(
    { webhookId: id, status: statusFilter || undefined, limit: 50 },
    { pollingInterval: 10000 }
  )

  return (
    <AppShell title="Webhook Deliveries" subtitle={webhook?.url ?? id}>
      <div className="stack">
        <div>
          <button onClick={() => navigate('/webhooks')}>← Back to Webhooks</button>
        </div>

        {webhook && (
          <div className="panel-card">
            <p className="section-title">Webhook</p>
            <div style={{ marginTop: '0.5rem', fontSize: '0.875rem' }}>
              <div style={{ fontFamily: 'monospace', wordBreak: 'break-all' }}>{webhook.url}</div>
              <div style={{ color: '#6b7280', marginTop: '0.25rem' }}>
                Events: {webhook.eventTypes.join(', ')} · {webhook.enabled ? 'enabled' : 'disabled'}
              </div>
            </div>
          </div>
        )}

        <div className="panel-card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <p className="section-title">Delivery History</p>
            <select
              value={statusFilter}
              onChange={e => setStatusFilter(e.target.value)}
              style={{ fontSize: '0.875rem' }}
            >
              <option value="">All</option>
              <option value="success">Success</option>
              <option value="retrying">Retrying</option>
              <option value="dead_letter">Dead letter</option>
              <option value="pending">Pending</option>
            </select>
          </div>

          {isLoading ? (
            <p className="panel-empty" style={{ marginTop: '0.5rem' }}>Loading…</p>
          ) : deliveries.length === 0 ? (
            <p className="panel-empty" style={{ marginTop: '0.5rem' }}>No deliveries found.</p>
          ) : (
            <div className="list-card" style={{ marginTop: '0.5rem' }}>
              {deliveries.map(d => (
                <div
                  key={d.id}
                  className="list-row"
                  style={{ cursor: 'pointer', justifyContent: 'space-between' }}
                  onClick={() => setSelectedDelivery(d.id === selectedDelivery ? null : d.id)}
                >
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <span style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>{d.eventType}</span>
                    <span style={{ fontSize: '0.75rem', color: '#6b7280', marginLeft: '0.5rem' }}>
                      {new Date(d.createdAt).toLocaleString()}
                    </span>
                  </div>
                  <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', flexShrink: 0 }}>
                    <StatusBadge status={d.status} />
                    <span style={{ fontSize: '0.8rem', color: '#6b7280' }}>
                      {d.lastStatusCode ?? '—'} · {d.attempts} att.
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {selectedDelivery && (
        <DeliveryPanel
          deliveryId={selectedDelivery}
          onClose={() => setSelectedDelivery(null)}
        />
      )}
    </AppShell>
  )
}
