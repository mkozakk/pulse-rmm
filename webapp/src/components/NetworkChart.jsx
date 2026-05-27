import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer, Legend } from 'recharts'
import { formatBytes } from './SystemInfoPanel'

export default function NetworkChart({ rxSamples, txSamples, nic }) {
  const deltas = computeDeltas(rxSamples, txSamples)

  if (deltas.length < 1) {
    return <p className="panel-empty">Need at least two samples to compute rate.</p>
  }

  return (
    <div className="chart-card">
      <h3>Network ({nic})</h3>
      <ResponsiveContainer width="100%" height={220}>
        <LineChart data={deltas}>
          <XAxis dataKey="t" tick={{ fontSize: 11 }} interval="preserveStartEnd" />
          <YAxis tickFormatter={v => `${formatBytes(v)}/s`} tick={{ fontSize: 11 }} width={80} />
          <Tooltip formatter={v => `${formatBytes(v)}/s`} />
          <Legend />
          <Line type="monotone" dataKey="rx" name="RX" dot={false} strokeWidth={2} stroke="#2563eb" />
          <Line type="monotone" dataKey="tx" name="TX" dot={false} strokeWidth={2} stroke="#dc2626" />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}

function computeDeltas(rxSamples = [], txSamples = []) {
  const result = []
  const len = Math.min(rxSamples.length, txSamples.length)
  for (let i = 1; i < len; i++) {
    const rxPrev = rxSamples[i - 1]
    const rxCur = rxSamples[i]
    const txPrev = txSamples[i - 1]
    const txCur = txSamples[i]
    const dtSec = (new Date(rxCur.sampledAt) - new Date(rxPrev.sampledAt)) / 1000
    if (dtSec <= 0) continue
    result.push({
      t: new Date(rxCur.sampledAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      rx: Math.max(0, (rxCur.value - rxPrev.value) / dtSec),
      tx: Math.max(0, (txCur.value - txPrev.value) / dtSec)
    })
  }
  return result
}
