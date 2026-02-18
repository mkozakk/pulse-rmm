import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts'

export default function MetricChart({ data, label }) {
  const points = data.map(d => ({
    t: new Date(d.sampledAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    v: Math.round(d.value * 10) / 10
  }))

  return (
    <div className="chart-card">
      <h3>{label}</h3>
      {points.length === 0 && <p className="panel-empty">No data yet.</p>}
      {points.length > 0 && (
        <ResponsiveContainer width="100%" height={200}>
          <LineChart data={points}>
            <XAxis dataKey="t" tick={{ fontSize: 11 }} interval="preserveStartEnd" />
            <YAxis domain={[0, 100]} unit="%" tick={{ fontSize: 11 }} width={40} />
            <Tooltip formatter={v => [`${v}%`, label]} />
            <Line type="monotone" dataKey="v" dot={false} strokeWidth={2} stroke="#2563eb" />
          </LineChart>
        </ResponsiveContainer>
      )}
    </div>
  )
}
