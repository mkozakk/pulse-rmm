import { formatBytes } from './SystemInfoPanel'

export default function PerDiskTable({ disks, samples }) {
  const latestByMount = new Map()
  for (const s of samples || []) {
    const mount = s.labels?.mount
    if (!mount) continue
    const entry = latestByMount.get(mount) || {}
    entry[s.type] = s.value
    latestByMount.set(mount, entry)
  }

  const rows = (disks || []).map(d => {
    const m = latestByMount.get(d.mountpoint) || {}
    const used = m['disk.used_bytes']
    const free = m['disk.free_bytes']
    const total = m['disk.total_bytes'] ?? d.totalBytes
    const pct = used != null && total ? Math.round((used / total) * 100) : null
    return { ...d, used, free, total, pct }
  })

  if (rows.length === 0) {
    return <p className="panel-empty">No disks reported.</p>
  }

  return (
    <table className="info-table">
      <thead>
        <tr>
          <th>Device</th>
          <th>Mount</th>
          <th>FS</th>
          <th>Used</th>
          <th>Free</th>
          <th>Total</th>
          <th>Usage</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((r, i) => (
          <tr key={i}>
            <td>{r.device}</td>
            <td>{r.mountpoint}</td>
            <td>{r.fstype}</td>
            <td>{formatBytes(r.used)}</td>
            <td>{formatBytes(r.free)}</td>
            <td>{formatBytes(r.total)}</td>
            <td>{r.pct != null ? `${r.pct}%` : '-'}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
