import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer, Legend } from 'recharts'

const COLORS = ['#2563eb', '#dc2626', '#16a34a', '#9333ea', '#ea580c', '#0891b2', '#ca8a04', '#db2777']

export default function PerCoreCpuChart({ samples }) {
  if (!samples || samples.length === 0) {
    return <p className="panel-empty">No per-core data yet.</p>
  }

  const byTime = new Map()
  const cores = new Set()
  for (const s of samples) {
    const core = s.labels?.core
    if (core == null) continue
    cores.add(core)
    const t = new Date(s.sampledAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    const row = byTime.get(t) || { t }
    row[`core${core}`] = Math.round(s.value * 10) / 10
    byTime.set(t, row)
  }

  const data = Array.from(byTime.values())
  const sortedCores = Array.from(cores).sort((a, b) => Number(a) - Number(b))

  return (
    <div className="chart-card">
      <h3>CPU per core</h3>
      <ResponsiveContainer width="100%" height={240}>
        <LineChart data={data}>
          <XAxis dataKey="t" tick={{ fontSize: 11 }} interval="preserveStartEnd" />
          <YAxis domain={[0, 100]} unit="%" tick={{ fontSize: 11 }} width={40} />
          <Tooltip />
          <Legend />
          {sortedCores.map((core, i) => (
            <Line
              key={core}
              type="monotone"
              dataKey={`core${core}`}
              dot={false}
              strokeWidth={1.5}
              stroke={COLORS[i % COLORS.length]}
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}
