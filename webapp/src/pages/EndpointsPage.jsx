import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Search, Terminal, Monitor } from 'lucide-react'
import { useGetEndpointsQuery } from '../api/pulseApi'
import AppShell from '../components/AppShell'

export default function EndpointsPage() {
  const { data: endpoints = [], isLoading, isError } = useGetEndpointsQuery(undefined, {
    pollingInterval: 30000
  })
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('all')
  const [osFilter, setOsFilter] = useState('all')

  const osList = [...new Set(endpoints.map(ep => ep.os).filter(Boolean))]

  const filtered = endpoints.filter(ep => {
    if (statusFilter !== 'all' && ep.status !== statusFilter) return false
    if (osFilter !== 'all' && ep.os !== osFilter) return false
    if (search) {
      const q = search.toLowerCase()
      return ep.hostname?.toLowerCase().includes(q) || ep.id?.toLowerCase().includes(q)
    }
    return true
  })

  const onlineCount = endpoints.filter(ep => ep.status === 'online').length

  return (
    <AppShell
      title="Endpoints"
      subtitle="Fleet overview - click any endpoint to inspect metrics and access it remotely."
    >
      <div className="endpoints-toolbar">
        <div className="endpoints-search">
          <Search size={14} className="endpoints-search-icon" />
          <input
            className="endpoints-search-input"
            placeholder="Search by hostname or ID…"
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
        </div>
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', flexWrap: 'wrap' }}>
          <select className="endpoints-filter-select" value={statusFilter} onChange={e => setStatusFilter(e.target.value)}>
            <option value="all">All statuses</option>
            <option value="online">Online</option>
            <option value="offline">Offline</option>
          </select>
          {osList.length > 0 && (
            <select className="endpoints-filter-select" value={osFilter} onChange={e => setOsFilter(e.target.value)}>
              <option value="all">All OS</option>
              {osList.map(os => <option key={os} value={os}>{os}</option>)}
            </select>
          )}
          <span className="endpoints-stats">
            <span className="stat-online">{onlineCount} online</span>
            <span className="muted">/ {endpoints.length} total</span>
          </span>
        </div>
      </div>

      {isLoading && <p className="panel-empty">Loading endpoints…</p>}
      {isError && <p className="error">Failed to load endpoints.</p>}

      {!isLoading && !isError && (
        <section className="panel-card">
          <table className="endpoints-table">
            <thead>
              <tr>
                <th style={{ width: 32 }}></th>
                <th>Hostname</th>
                <th>OS</th>
                <th>Last seen</th>
                <th style={{ width: 72 }}>Access</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map(ep => (
                <tr key={ep.id}>
                  <td>
                    <span className={`status-dot status-dot-${ep.status ?? 'unknown'}`} />
                  </td>
                  <td>
                    <Link className="endpoint-hostname-link" to={`/endpoints/${ep.id}`}>{ep.hostname}</Link>
                    <div className="table-secondary">{ep.id}</div>
                  </td>
                  <td><span className="badge-gray">{ep.os}</span></td>
                  <td className="muted">
                    {ep.lastSeenAt ? new Date(ep.lastSeenAt).toLocaleString() : '-'}
                  </td>
                  <td>
                    <div className="endpoints-quick-actions">
                      <Link
                        to={`/endpoints/${ep.id}/shell`}
                        className={`quick-action-btn${ep.status !== 'online' ? ' disabled' : ''}`}
                        title="Terminal"
                        aria-disabled={ep.status !== 'online'}
                        onClick={ep.status !== 'online' ? e => e.preventDefault() : undefined}
                      >
                        <Terminal size={13} />
                      </Link>
                      <Link
                        to={`/endpoints/${ep.id}/desktop`}
                        className={`quick-action-btn${ep.status !== 'online' ? ' disabled' : ''}`}
                        title="Desktop"
                        aria-disabled={ep.status !== 'online'}
                        onClick={ep.status !== 'online' ? e => e.preventDefault() : undefined}
                      >
                        <Monitor size={13} />
                      </Link>
                    </div>
                  </td>
                </tr>
              ))}
              {filtered.length === 0 && (
                <tr>
                  <td colSpan={5} className="panel-empty" style={{ textAlign: 'center', padding: '2.5rem' }}>
                    {endpoints.length === 0 ? 'No endpoints enrolled yet.' : 'No endpoints match your filter.'}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </section>
      )}
    </AppShell>
  )
}
