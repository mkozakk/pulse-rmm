import { useState, useEffect } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { useGetMetricsQuery } from '../api/pulseApi'
import MetricChart from '../components/MetricChart'

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

  const cpu = useMetric(id, range.hours, 'cpu', tick)
  const ram = useMetric(id, range.hours, 'ram', tick)
  const disk = useMetric(id, range.hours, 'disk', tick)

  return (
    <div className="page">
      <header className="page-header">
        <Link to="/endpoints">← Back</Link>
        <h1>Endpoint metrics</h1>
        <button onClick={() => navigate(`/endpoints/${id}/shell`)}>Open Terminal</button>
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
      </header>

      <MetricChart data={cpu.data ?? []} label="CPU" />
      <MetricChart data={ram.data ?? []} label="RAM" />
      <MetricChart data={disk.data ?? []} label="Disk" />
    </div>
  )
}
