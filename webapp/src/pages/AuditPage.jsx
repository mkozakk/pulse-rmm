import { useState } from 'react'
import AppShell from '../components/AppShell'
import { useGetAuditLogQuery } from '../api/pulseApi'

export default function AuditPage() {
  const [filters, setFilters] = useState({ user: '', endpoint: '', from: '', to: '' })
  const [applied, setApplied] = useState({})
  const [page, setPage] = useState(0)
  const [expanded, setExpanded] = useState(null)

  const { data, isLoading, isError } = useGetAuditLogQuery({ ...applied, page })

  const events = data?.content ?? []
  const totalPages = data?.totalPages ?? 0

  function set(field) {
    return e => setFilters(f => ({ ...f, [field]: e.target.value }))
  }

  function handleSearch(e) {
    e.preventDefault()
    setApplied({ ...filters })
    setPage(0)
  }

  function exportUrl(format) {
    const params = new URLSearchParams()
    if (applied.user) params.set('user', applied.user)
    if (applied.endpoint) params.set('endpoint', applied.endpoint)
    if (applied.from) params.set('from', applied.from)
    if (applied.to) params.set('to', applied.to)
    params.set('format', format)
    return `/api/audit/export?${params}`
  }

  return (
    <AppShell title="Audit Log" subtitle="Immutable record of all user actions">
      <div className="stack">
        <div className="panel-card">
          <p className="section-title">Filters</p>
          <form onSubmit={handleSearch} style={{ marginTop: '0.75rem' }}>
            <div className="form-grid">
              <input placeholder="User UUID" value={filters.user} onChange={set('user')} />
              <input placeholder="Endpoint UUID" value={filters.endpoint} onChange={set('endpoint')} />
              <input type="datetime-local" placeholder="From" value={filters.from} onChange={set('from')} />
              <input type="datetime-local" placeholder="To" value={filters.to} onChange={set('to')} />
              <button type="submit">Search</button>
            </div>
          </form>
        </div>

        <div className="panel-card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <p className="section-title">Events</p>
            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <a href={exportUrl('csv')} download="audit.csv" className="button-link">Export CSV</a>
              <a href={exportUrl('json')} download="audit.ndjson" className="button-link">Export JSON</a>
            </div>
          </div>

          {isLoading && <p className="muted" style={{ marginTop: '0.5rem' }}>Loading…</p>}
          {isError && <p className="muted" style={{ marginTop: '0.5rem' }}>Failed to load audit log.</p>}

          {!isLoading && !isError && events.length === 0 && (
            <p className="muted" style={{ marginTop: '0.5rem' }}>No events found.</p>
          )}

          {events.length > 0 && (
            <div className="list-card" style={{ marginTop: '0.5rem' }}>
              <div className="list-row" style={{ fontWeight: 600 }}>
                <span style={{ flex: 2 }}>Timestamp</span>
                <span style={{ flex: 1 }}>User</span>
                <span style={{ flex: 2 }}>Action</span>
                <span style={{ flex: 2 }}>Permission</span>
                <span style={{ flex: 2 }}>Endpoint</span>
                <span style={{ flex: 1 }}>Payload</span>
              </div>
              {events.map(ev => (
                <div key={ev.id}>
                  <div className="list-row">
                    <span style={{ flex: 2 }}>{new Date(ev.createdAt).toLocaleString()}</span>
                    <span style={{ flex: 1 }} className="muted">{ev.username ?? ev.userId?.slice(0, 8)}</span>
                    <span style={{ flex: 2 }}>{ev.action}</span>
                    <span style={{ flex: 2 }} className="muted">{ev.permissionUsed}</span>
                    <span style={{ flex: 2 }} className="muted">{ev.endpointId?.slice(0, 8) ?? '—'}</span>
                    <span style={{ flex: 1 }}>
                      {ev.payload
                        ? <button onClick={() => setExpanded(expanded === ev.id ? null : ev.id)}>
                            {expanded === ev.id ? 'hide' : 'show'}
                          </button>
                        : <span className="muted">—</span>
                      }
                    </span>
                  </div>
                  {expanded === ev.id && ev.payload && (
                    <pre style={{ margin: '0 0 0.5rem 0', padding: '0.5rem', background: 'var(--bg-subtle, #f5f5f5)', fontSize: '0.75rem', overflowX: 'auto' }}>
                      {JSON.stringify(ev.payload, null, 2)}
                    </pre>
                  )}
                </div>
              ))}
            </div>
          )}

          {totalPages > 1 && (
            <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.75rem', alignItems: 'center' }}>
              <button onClick={() => setPage(p => p - 1)} disabled={page === 0}>Prev</button>
              <span className="muted">{page + 1} / {totalPages}</span>
              <button onClick={() => setPage(p => p + 1)} disabled={page >= totalPages - 1}>Next</button>
            </div>
          )}
        </div>
      </div>
    </AppShell>
  )
}
