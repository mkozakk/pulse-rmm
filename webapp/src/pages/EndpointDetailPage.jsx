import { useState, useEffect, useMemo } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { Terminal, Monitor, FolderOpen, Activity, ArrowLeft } from 'lucide-react'
import {
  useGetEndpointQuery,
  useGetMetricsQuery,
  useGetSystemInfoQuery
} from '../api/pulseApi'
import MetricChart from '../components/MetricChart'
import SystemInfoPanel from '../components/SystemInfoPanel'
import PerCoreCpuChart from '../components/PerCoreCpuChart'
import PerDiskTable from '../components/PerDiskTable'
import NetworkChart from '../components/NetworkChart'
import AppShell from '../components/AppShell'

const RANGES = [
  { label: '1h', hours: 1 },
  { label: '6h', hours: 6 },
  { label: '24h', hours: 24 },
  { label: '7d', hours: 168 }
]

const SECTIONS = [
  { id: 'overview', label: 'Overview' },
  { id: 'cpu', label: 'CPU' },
  { id: 'storage', label: 'Storage' },
  { id: 'network', label: 'Network' }
]

function metricColor(value) {
  if (value == null) return '#94a3b8'
  if (value < 60) return '#16a34a'
  if (value < 80) return '#d97706'
  return '#dc2626'
}

function MetricStatCard({ label, value }) {
  const color = metricColor(value)
  const sub = value == null ? 'No data'
    : value < 60 ? 'Normal'
    : value < 80 ? 'Elevated'
    : 'High'

  return (
    <div className="metric-stat-card">
      <p className="metric-stat-label">{label}</p>
      <p className="metric-stat-value" style={{ color }}>
        {value != null ? `${value}%` : '-'}
      </p>
      <p className="metric-stat-sub">{sub}</p>
    </div>
  )
}

function useMetric(id, hours, type, tick) {
  const to = tick.toISOString()
  const from = new Date(tick - hours * 3600 * 1000).toISOString()
  return useGetMetricsQuery({ id, from, to, type })
}

export default function EndpointDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [range, setRange] = useState(RANGES[0])
  const [section, setSection] = useState('overview')
  const [tick, setTick] = useState(() => new Date())

  useEffect(() => {
    const interval = setInterval(() => setTick(new Date()), 30000)
    return () => clearInterval(interval)
  }, [])

  const endpoint = useGetEndpointQuery(id)
  const sysinfo = useGetSystemInfoQuery(id)

  const cpu = useMetric(id, range.hours, 'cpu', tick)
  const ram = useMetric(id, range.hours, 'ram', tick)
  const disk = useMetric(id, range.hours, 'disk', tick)
  const perCore = useMetric(id, range.hours, 'cpu.core', tick)

  const diskUsedSamples = useMetric(id, range.hours, 'disk.used_bytes', tick)
  const diskFreeSamples = useMetric(id, range.hours, 'disk.free_bytes', tick)
  const diskTotalSamples = useMetric(id, range.hours, 'disk.total_bytes', tick)

  const firstNic = sysinfo.data?.nics?.find(n => n.name && n.name !== 'lo')?.name
    || sysinfo.data?.nics?.[0]?.name
  const rxSamples = useGetMetricsQuery(
    firstNic
      ? { id, from: new Date(tick - range.hours * 3600 * 1000).toISOString(), to: tick.toISOString(), type: 'net.rx_bytes', labels: { nic: firstNic } }
      : { skip: true },
    { skip: !firstNic }
  )
  const txSamples = useGetMetricsQuery(
    firstNic
      ? { id, from: new Date(tick - range.hours * 3600 * 1000).toISOString(), to: tick.toISOString(), type: 'net.tx_bytes', labels: { nic: firstNic } }
      : { skip: true },
    { skip: !firstNic }
  )

  const latestDiskSamples = useMemo(() => {
    const all = [
      ...(diskUsedSamples.data || []),
      ...(diskFreeSamples.data || []),
      ...(diskTotalSamples.data || [])
    ]
    if (all.length === 0) return []
    const byKey = new Map()
    for (const s of all) {
      const key = `${s.labels?.mount}|${s.type || ''}`
      const prev = byKey.get(key)
      if (!prev || new Date(s.sampledAt) > new Date(prev.sampledAt)) {
        byKey.set(key, s)
      }
    }
    return Array.from(byKey.values())
  }, [diskUsedSamples.data, diskFreeSamples.data, diskTotalSamples.data])

  const ep = endpoint.data
  const canOpenRemote = ep?.status === 'online'

  const latestCpu = cpu.data?.length > 0 ? Math.round(cpu.data[cpu.data.length - 1].value) : null
  const latestRam = ram.data?.length > 0 ? Math.round(ram.data[ram.data.length - 1].value) : null
  const latestDisk = disk.data?.length > 0 ? Math.round(disk.data[disk.data.length - 1].value) : null

  return (
    <AppShell
      title={ep?.hostname ?? 'Endpoint details'}
      subtitle={ep ? `${ep.os} · ${ep.id.slice(0, 8)}` : 'Inspect live resource usage and jump into remote access.'}
    >
      {endpoint.isLoading && <p className="panel-empty">Loading…</p>}
      {endpoint.isError && <p className="error">Failed to load endpoint details.</p>}

      <div className="stack">
        {/* Access bar: status + hostname + remote access buttons */}
        <div className="endpoint-access-bar">
          <span className={`status-dot status-dot-${ep?.status ?? 'unknown'}`} />
          <span className="endpoint-access-bar-name">{ep?.hostname ?? '-'}</span>
          <button className="icon-btn endpoint-action" disabled={!canOpenRemote} onClick={() => navigate(`/endpoints/${id}/shell`)}>
            <Terminal size={14} />Terminal
          </button>
          <button className="icon-btn endpoint-action" disabled={!canOpenRemote} onClick={() => navigate(`/endpoints/${id}/desktop`)}>
            <Monitor size={14} />Desktop
          </button>
          <button className="icon-btn endpoint-action" disabled={!canOpenRemote} onClick={() => navigate(`/endpoints/${id}/files`)}>
            <FolderOpen size={14} />Files
          </button>
          <button className="icon-btn endpoint-action" disabled={!canOpenRemote} onClick={() => navigate(`/endpoints/${id}/processes`)}>
            <Activity size={14} />Processes
          </button>
        </div>

        <div className="endpoint-detail-layout">
          {/* Sidebar: identity + system info */}
          <div className="endpoint-detail-sidebar">
            <div className="panel-card stack">
              <p className="section-title">Endpoint</p>
              <div className="sysinfo-kv">
                <span className="sysinfo-key">Status</span>
                <span className="sysinfo-val">
                  <span className={`badge badge-${ep?.status ?? 'offline'}`}>{ep?.status ?? '-'}</span>
                </span>
              </div>
              <div className="sysinfo-kv">
                <span className="sysinfo-key">OS</span>
                <span className="sysinfo-val">{ep?.os ?? '-'}</span>
              </div>
              <div className="sysinfo-kv">
                <span className="sysinfo-key">ID</span>
                <span className="sysinfo-val" style={{ fontFamily: 'monospace', fontSize: 11 }} title={id}>
                  {id.slice(0, 12)}…
                </span>
              </div>
            </div>

            <SystemInfoPanel info={sysinfo.data} />
          </div>

          {/* Main: stat cards + charts */}
          <div className="endpoint-detail-main">
            <div className="metric-stat-row">
              <MetricStatCard label="CPU" value={latestCpu} />
              <MetricStatCard label="RAM" value={latestRam} />
              <MetricStatCard label="Disk" value={latestDisk} />
            </div>

            <div className="endpoint-chart-toolbar">
              <nav className="section-tabs" style={{ margin: 0, borderBottom: 'none' }}>
                {SECTIONS.map(s => (
                  <button
                    key={s.id}
                    className={section === s.id ? 'active' : ''}
                    onClick={() => setSection(s.id)}
                  >
                    {s.label}
                  </button>
                ))}
              </nav>
              <div className="range-switcher">
                {RANGES.map(r => (
                  <button
                    key={r.label}
                    className={range.label === r.label ? 'active' : ''}
                    onClick={() => { setRange(r); setTick(new Date()) }}
                  >
                    {r.label}
                  </button>
                ))}
              </div>
            </div>

            {section === 'overview' && (
              <div className="charts-grid">
                <MetricChart data={cpu.data ?? []} label="CPU" />
                <MetricChart data={ram.data ?? []} label="RAM" />
                <MetricChart data={disk.data ?? []} label="Disk" />
              </div>
            )}

            {section === 'cpu' && (
              <PerCoreCpuChart samples={perCore.data ?? []} />
            )}

            {section === 'storage' && (
              <PerDiskTable disks={sysinfo.data?.disks ?? []} samples={latestDiskSamples} />
            )}

            {section === 'network' && (
              firstNic
                ? <NetworkChart rxSamples={rxSamples.data ?? []} txSamples={txSamples.data ?? []} nic={firstNic} />
                : <p className="panel-empty">No network interfaces reported.</p>
            )}
          </div>
        </div>

        <div className="page-footer">
          <Link to="/endpoints" style={{ display: 'inline-flex', alignItems: 'center', gap: '0.3rem' }}>
            <ArrowLeft size={14} />Back to endpoints
          </Link>
        </div>
      </div>
    </AppShell>
  )
}
