function formatBytes(n) {
  if (n == null) return '—'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let v = Number(n)
  let i = 0
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024
    i++
  }
  return `${v.toFixed(v >= 10 || i === 0 ? 0 : 1)} ${units[i]}`
}

export default function SystemInfoPanel({ info }) {
  if (!info) {
    return <p className="panel-empty">No system info reported yet.</p>
  }

  return (
    <section className="system-info-panel">
      <div className="panel-card">
        <h3>CPU</h3>
        <p className="detail-value">{info.cpuModel || 'Unknown'}</p>
        <p className="detail-label">
          {info.cpuPhysical ?? '?'} physical / {info.cpuLogical ?? '?'} logical cores
          {info.cpuFreqMhz ? ` · ${Math.round(info.cpuFreqMhz)} MHz` : ''}
        </p>
      </div>

      <div className="panel-card">
        <h3>Memory</h3>
        <p className="detail-value">{formatBytes(info.ramTotal)}</p>
        <p className="detail-label">Swap: {formatBytes(info.swapTotal)}</p>
      </div>

      <div className="panel-card">
        <h3>Disks ({info.disks?.length ?? 0})</h3>
        <table className="info-table">
          <thead>
            <tr><th>Device</th><th>Mount</th><th>FS</th><th>Total</th></tr>
          </thead>
          <tbody>
            {(info.disks || []).map((d, i) => (
              <tr key={i}>
                <td>{d.device}</td>
                <td>{d.mountpoint}</td>
                <td>{d.fstype}</td>
                <td>{formatBytes(d.totalBytes)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="panel-card">
        <h3>Network interfaces ({info.nics?.length ?? 0})</h3>
        <table className="info-table">
          <thead>
            <tr><th>Name</th><th>MAC</th><th>Addresses</th><th>MTU</th></tr>
          </thead>
          <tbody>
            {(info.nics || []).map((n, i) => (
              <tr key={i}>
                <td>{n.name}</td>
                <td>{n.mac || '—'}</td>
                <td>{(n.addresses || []).join(', ') || '—'}</td>
                <td>{n.mtu}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}

export { formatBytes }
