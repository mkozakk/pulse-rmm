import { useState, useEffect, useMemo } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
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

function useMetric(id, hours, type, tick, labels) {
  const to = tick.toISOString()
  const from = new Date(tick - hours * 3600 * 1000).toISOString()
  return useGetMetricsQuery({ id, from, to, type, labels })
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

  const loading = endpoint.isLoading
  const error = endpoint.isError
  const canOpenRemote = endpoint.data?.status === 'online'

  return (
    <AppShell
      title={endpoint.data?.hostname ?? 'Endpoint details'}
      subtitle={endpoint.data ? `${endpoint.data.os} · ${endpoint.data.status}` : 'Inspect live resource usage and jump into remote access.'}
      actions={(
        <>
          <button className="endpoint-action" disabled={!canOpenRemote} onClick={() => navigate(`/endpoints/${id}/shell`)}>Open Terminal</button>
          <button className="endpoint-action" disabled={!canOpenRemote} onClick={() => navigate(`/endpoints/${id}/desktop`)}>Open Desktop</button>
          <button className="endpoint-action" disabled={!canOpenRemote} onClick={() => navigate(`/endpoints/${id}/files`)}>Browse Files</button>
          <button className="endpoint-action" disabled={!canOpenRemote} onClick={() => navigate(`/endpoints/${id}/processes`)}>Processes</button>
        </>
      )}
    >
      {loading && <p className="panel-empty">Loading metrics...</p>}
      {error && <p className="error">Failed to load endpoint details.</p>}

      <section className="detail-hero panel-card">
        <div>
          <p className="detail-label">Endpoint ID</p>
          <p className="detail-value">{id}</p>
        </div>
        <div>
          <p className="detail-label">Current status</p>
          <p className="detail-value">
            <span className={`badge badge-${endpoint.data?.status ?? 'offline'}`}>{endpoint.data?.status ?? 'unknown'}</span>
          </p>
        </div>
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
      </section>

      <SystemInfoPanel info={sysinfo.data} />

      <nav className="section-tabs">
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

      <div className="page-footer">
        <Link to="/endpoints">Back to endpoints</Link>
      </div>
    </AppShell>
  )
}
