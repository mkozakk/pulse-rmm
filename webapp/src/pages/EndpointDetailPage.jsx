import { useState, useEffect } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { useGetEndpointQuery, useGetMetricsQuery } from '../api/pulseApi'
import MetricChart from '../components/MetricChart'
import AppShell from '../components/AppShell'

const RANGES = [
  { label: '1h', hours: 1 },
  { label: '6h', hours: 6 },
  { label: '24h', hours: 24 },
  { label: '7d', hours: 168 }
]

function useMetric(id, hours, type, tick) {
  const to = tick.toISOString()
  const from = new Date(tick - hours * 3600 * 1000).toISOString()
  return useGetMetricsQuery({ id, from, to, type })
}

export default function EndpointDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [range, setRange] = useState(RANGES[0])
  const [tick, setTick] = useState(() => new Date())

  useEffect(() => {
    const interval = setInterval(() => setTick(new Date()), 30000)
    return () => clearInterval(interval)
  }, [])

  const endpoint = useGetEndpointQuery(id)
  const cpu = useMetric(id, range.hours, 'cpu', tick)
  const ram = useMetric(id, range.hours, 'ram', tick)
  const disk = useMetric(id, range.hours, 'disk', tick)
  const loading = endpoint.isLoading || cpu.isLoading || ram.isLoading || disk.isLoading
  const error = endpoint.isError || cpu.isError || ram.isError || disk.isError
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

      <div className="charts-grid">
        <MetricChart data={cpu.data ?? []} label="CPU" />
        <MetricChart data={ram.data ?? []} label="RAM" />
        <MetricChart data={disk.data ?? []} label="Disk" />
      </div>

      <div className="page-footer">
        <Link to="/endpoints">Back to endpoints</Link>
      </div>
    </AppShell>
  )
}
