import { useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { ArrowLeft, RefreshCw, Search, XCircle } from 'lucide-react'
import {
  useGetEndpointQuery,
  useGetLatestProcessesQuery,
  useRefreshProcessesMutation,
  useKillProcessMutation
} from '../api/pulseApi'
import AppShell from '../components/AppShell'

function formatBytes(n) {
  if (!n) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let v = n
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024
    i++
  }
  return `${v.toFixed(1)} ${units[i]}`
}

export default function EndpointProcessesPage() {
  const { id } = useParams()
  const [sortKey, setSortKey] = useState('cpuPercent')
  const [sortDir, setSortDir] = useState('desc')
  const [search, setSearch] = useState('')
  const { data: ep } = useGetEndpointQuery(id)
  const latest = useGetLatestProcessesQuery(id)
  const [refresh, refreshState] = useRefreshProcessesMutation()
  const [killProcess] = useKillProcessMutation()
  const hostname = ep?.hostname ?? id.slice(0, 8)

  const onRefresh = async () => {
    await refresh(id)
    setTimeout(() => latest.refetch(), 1500)
  }

  const onKill = async (pid, name) => {
    if (!window.confirm(`Kill process ${name} (PID ${pid})?`)) return
    await killProcess({ endpointId: id, pid })
    setTimeout(() => latest.refetch(), 1500)
  }

  const procs = (latest.data?.processes ?? [])
    .filter(p => !search || p.name?.toLowerCase().includes(search.toLowerCase()) || String(p.pid).includes(search))
    .slice()
    .sort((a, b) => {
      const av = a[sortKey] ?? 0
      const bv = b[sortKey] ?? 0
      if (av === bv) return 0
      const cmp = av < bv ? -1 : 1
      return sortDir === 'asc' ? cmp : -cmp
    })

  const toggleSort = (key) => {
    if (key === sortKey) setSortDir(sortDir === 'asc' ? 'desc' : 'asc')
    else { setSortKey(key); setSortDir('desc') }
  }

  const sortArrow = (key) => key === sortKey ? (sortDir === 'asc' ? ' ↑' : ' ↓') : null

  return (
    <AppShell title={`Processes - ${hostname}`}>
      <div className="stack">
        <div className="endpoint-access-bar">
          <Link to={`/endpoints/${id}`} className="icon-btn endpoint-action">
            <ArrowLeft size={14} />Endpoint
          </Link>
          <span className="remote-sep" />
          <span className={`status-dot status-dot-${ep?.status ?? 'unknown'}`} />
          <span className="endpoint-access-bar-name">{hostname}</span>
          <div style={{ flex: 1 }} />
          {procs.length > 0 && (
            <span className="muted" style={{ fontSize: 12 }}>{procs.length} processes</span>
          )}
          <button className="icon-btn endpoint-action" onClick={onRefresh} disabled={refreshState.isLoading}>
            <RefreshCw size={14} />{refreshState.isLoading ? 'Requesting…' : 'Refresh'}
          </button>
        </div>

        <div className="processes-search-bar">
          <Search size={13} className="processes-search-icon" />
          <input
            className="processes-search-input"
            placeholder="Filter by name or PID…"
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
        </div>

        {latest.isError && latest.error?.status === 404 && (
          <p className="panel-empty">No snapshot yet. Click Refresh to query the agent.</p>
        )}
        {latest.isLoading && <p className="panel-empty">Loading…</p>}

        {procs.length > 0 && (
          <div className="panel-card">
            <table className="processes-table">
              <thead>
                <tr>
                  <th onClick={() => toggleSort('pid')}>PID{sortArrow('pid')}</th>
                  <th onClick={() => toggleSort('name')}>Name{sortArrow('name')}</th>
                  <th onClick={() => toggleSort('username')}>User{sortArrow('username')}</th>
                  <th onClick={() => toggleSort('cpuPercent')}>CPU %{sortArrow('cpuPercent')}</th>
                  <th onClick={() => toggleSort('memoryBytes')}>Memory{sortArrow('memoryBytes')}</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {procs.map(p => (
                  <tr key={p.pid}>
                    <td className="proc-pid">{p.pid}</td>
                    <td>{p.name}</td>
                    <td className="col-muted">{p.username}</td>
                    <td className="col-right">{(p.cpuPercent ?? 0).toFixed(1)}</td>
                    <td className="col-right">{formatBytes(p.memoryBytes)}</td>
                    <td className="col-right">
                      <button className="icon-btn endpoint-action proc-kill-btn" onClick={() => onKill(p.pid, p.name)}>
                        <XCircle size={12} />Kill
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </AppShell>
  )
}
