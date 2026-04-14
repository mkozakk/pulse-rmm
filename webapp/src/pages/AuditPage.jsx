import { useState } from 'react'
import { Search, Download, ChevronLeft, ChevronRight, ChevronDown, ChevronUp } from 'lucide-react'
import AppShell from '../components/AppShell'
import { useGetAuditLogQuery, useGetUsersQuery, useGetEndpointsQuery } from '../api/pulseApi'

export default function AuditPage() {
  const [filters, setFilters] = useState({ user: '', endpoint: '', from: '', to: '' })
  const [applied, setApplied] = useState({})
  const [page, setPage] = useState(0)
  const [expanded, setExpanded] = useState(null)

  const { data, isLoading, isError } = useGetAuditLogQuery({ ...applied, page })
  const { data: users = [] } = useGetUsersQuery()
  const { data: endpoints = [] } = useGetEndpointsQuery()

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
    <AppShell title="Audit Log">
      <div className="stack">
        <section className="panel-card stack">
          <h2 className="section-title">Filters</h2>
          <form onSubmit={handleSearch} className="stack">
            <div className="form-row">
              <div className="form-field">
                <label className="field-label">User</label>
                <select value={filters.user} onChange={set('user')}>
                  <option value="">All users</option>
                  {users.map(u => <option key={u.id} value={u.id}>{u.username}</option>)}
                </select>
              </div>
              <div className="form-field">
                <label className="field-label">Endpoint</label>
                <select value={filters.endpoint} onChange={set('endpoint')}>
                  <option value="">All endpoints</option>
                  {endpoints.map(ep => <option key={ep.id} value={ep.id}>{ep.hostname}</option>)}
                </select>
              </div>
              <div className="form-field">
                <label className="field-label">From</label>
                <input type="datetime-local" value={filters.from} onChange={set('from')} />
              </div>
              <div className="form-field">
                <label className="field-label">To</label>
                <input type="datetime-local" value={filters.to} onChange={set('to')} />
              </div>
            </div>
            <div className="form-actions">
              <button type="submit" className="icon-btn btn-primary"><Search size={14} />Search</button>
            </div>
          </form>
        </section>

        <section className="panel-card stack">
          <div className="audit-events-header">
            <h2 className="section-title">Events{events.length > 0 && ` (page ${page + 1})`}</h2>
            <div className="audit-export-btns">
              <a href={exportUrl('csv')} download="audit.csv" className="icon-btn endpoint-action"><Download size={13} />Export CSV</a>
              <a href={exportUrl('json')} download="audit.ndjson" className="icon-btn endpoint-action"><Download size={13} />Export JSON</a>
            </div>
          </div>

          {isLoading && <p className="panel-empty">Loading...</p>}
          {isError && <p className="panel-empty">Failed to load audit log.</p>}
          {!isLoading && !isError && events.length === 0 && <p className="panel-empty">No events found.</p>}

          {events.length > 0 && (
            <table className="enrolment-table">
              <thead>
                <tr>
                  <th>Timestamp</th>
                  <th>User</th>
                  <th>Action</th>
                  <th>Permission</th>
                  <th>Endpoint</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {events.map(ev => (
                  <>
                    <tr key={ev.id}>
                      <td style={{ whiteSpace: 'nowrap', fontSize: 12 }}>{new Date(ev.createdAt).toLocaleString()}</td>
                      <td className="col-muted">{ev.username ?? ev.userId?.slice(0, 8)}</td>
                      <td style={{ fontWeight: 500 }}>{ev.action}</td>
                      <td className="col-muted" style={{ fontFamily: 'monospace', fontSize: 12 }}>{ev.permissionUsed}</td>
                      <td className="col-muted" style={{ fontFamily: 'monospace', fontSize: 12 }}>{ev.endpointId?.slice(0, 8) ?? '-'}</td>
                      <td className="col-right">
                        {ev.payload
                          ? (
                            <button className="icon-btn endpoint-action" onClick={() => setExpanded(expanded === ev.id ? null : ev.id)}>
                              {expanded === ev.id ? <><ChevronUp size={13} />hide</> : <><ChevronDown size={13} />show</>}
                            </button>
                          )
                          : <span className="col-muted">-</span>
                        }
                      </td>
                    </tr>
                    {expanded === ev.id && ev.payload && (
                      <tr key={ev.id + '-payload'}>
                        <td colSpan={6} style={{ padding: '0 0 0.5rem 1rem' }}>
                          <pre className="audit-payload-pre">
                            {JSON.stringify(ev.payload, null, 2)}
                          </pre>
                        </td>
                      </tr>
                    )}
                  </>
                ))}
              </tbody>
            </table>
          )}

          {totalPages > 1 && (
            <div className="audit-pagination">
              <button className="icon-btn endpoint-action" onClick={() => setPage(p => p - 1)} disabled={page === 0}>
                <ChevronLeft size={14} />Prev
              </button>
              <span className="col-muted" style={{ fontSize: 13 }}>{page + 1} / {totalPages}</span>
              <button className="icon-btn endpoint-action" onClick={() => setPage(p => p + 1)} disabled={page >= totalPages - 1}>
                Next<ChevronRight size={14} />
              </button>
            </div>
          )}
        </section>
      </div>
    </AppShell>
  )
}
