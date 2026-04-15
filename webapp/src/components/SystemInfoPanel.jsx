function formatBytes(n) {
  if (n == null) return '-'
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
  if (!info) return <p className="panel-empty">No system info reported yet.</p>

  return (
    <>
      <div className="panel-card stack">
        <p className="section-title">CPU</p>
        <div className="sysinfo-kv">
          <span className="sysinfo-key">Model</span>
          <span className="sysinfo-val" title={info.cpuModel}>{info.cpuModel || '-'}</span>
        </div>
        <div className="sysinfo-kv">
          <span className="sysinfo-key">Cores</span>
          <span className="sysinfo-val">{info.cpuPhysical ?? '?'} physical / {info.cpuLogical ?? '?'} logical</span>
        </div>
        {info.cpuFreqMhz != null && (
          <div className="sysinfo-kv">
            <span className="sysinfo-key">Freq</span>
            <span className="sysinfo-val">{Math.round(info.cpuFreqMhz)} MHz</span>
          </div>
        )}
      </div>

      <div className="panel-card stack">
        <p className="section-title">Memory</p>
        <div className="sysinfo-kv">
          <span className="sysinfo-key">RAM</span>
          <span className="sysinfo-val">{formatBytes(info.ramTotal)}</span>
        </div>
        <div className="sysinfo-kv">
          <span className="sysinfo-key">Swap</span>
          <span className="sysinfo-val">{formatBytes(info.swapTotal)}</span>
        </div>
      </div>

      {info.disks?.length > 0 && (
        <div className="panel-card stack">
          <p className="section-title">Disks ({info.disks.length})</p>
          {info.disks.map((d, i) => (
            <div key={i} className="sysinfo-item">
              <div className="sysinfo-item-head">
                <span>{d.device}</span>
                <span>{formatBytes(d.totalBytes)}</span>
              </div>
              <div className="sysinfo-item-sub">{d.mountpoint} · {d.fstype}</div>
            </div>
          ))}
        </div>
      )}

      {info.nics?.length > 0 && (
        <div className="panel-card stack">
          <p className="section-title">Network ({info.nics.length})</p>
          {info.nics.map((n, i) => (
            <div key={i} className="sysinfo-item">
              <div className="sysinfo-item-head">
                <span>{n.name}</span>
                <span style={{ color: '#374151', fontWeight: 400, fontSize: 11 }}>
                  {(n.addresses || []).join(', ') || '-'}
                </span>
              </div>
              {n.mac && <div className="sysinfo-item-sub">{n.mac}</div>}
            </div>
          ))}
        </div>
      )}
    </>
  )
}

export { formatBytes }
