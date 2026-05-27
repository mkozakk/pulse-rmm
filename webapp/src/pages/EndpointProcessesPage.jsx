import { useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import {
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
  const latest = useGetLatestProcessesQuery(id)
  const [refresh, refreshState] = useRefreshProcessesMutation()
  const [killProcess] = useKillProcessMutation()

  const onRefresh = async () => {
    await refresh(id)
    setTimeout(() => latest.refetch(), 1500)
  }

  const onKill = async (pid, name) => {
    if (!window.confirm(`Kill process ${name} (PID ${pid})?`)) return
    await killProcess({ endpointId: id, pid })
    setTimeout(() => latest.refetch(), 1500)
  }

  const procs = (latest.data?.processes ?? []).slice().sort((a, b) => {
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

  return (
    <AppShell
      title="Processes"
      subtitle={`Endpoint ${id}`}
      actions={(
        <button className="endpoint-action" onClick={onRefresh} disabled={refreshState.isLoading}>
          {refreshState.isLoading ? 'Requesting…' : 'Refresh'}
        </button>
      )}
    >
      {latest.isError && latest.error?.status === 404 && (
        <p className="panel-empty">No snapshot yet. Click Refresh to query the agent.</p>
      )}
      {latest.isLoading && <p className="panel-empty">Loading…</p>}

      {procs.length > 0 && (
        <table className="table">
          <thead>
            <tr>
              <th onClick={() => toggleSort('pid')}>PID</th>
              <th onClick={() => toggleSort('name')}>Name</th>
              <th onClick={() => toggleSort('username')}>User</th>
              <th onClick={() => toggleSort('cpuPercent')}>CPU %</th>
              <th onClick={() => toggleSort('memoryBytes')}>Memory</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {procs.map(p => (
              <tr key={p.pid}>
                <td>{p.pid}</td>
                <td>{p.name}</td>
                <td>{p.username}</td>
                <td>{(p.cpuPercent ?? 0).toFixed(1)}</td>
                <td>{formatBytes(p.memoryBytes)}</td>
                <td>
                  <button className="endpoint-action" onClick={() => onKill(p.pid, p.name)}>Kill</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <div className="page-footer">
        <Link to={`/endpoints/${id}`}>Back to endpoint</Link>
      </div>
    </AppShell>
  )
}
