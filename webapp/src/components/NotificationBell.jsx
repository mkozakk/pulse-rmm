import { useState, useEffect } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { useGetAlertsQuery, useAckAlertMutation } from '../api/pulseApi'
import { seedAlerts, removeAlert } from '../store/alertsSlice'
import { useAlertStream } from '../hooks/useAlertStream'

export default function NotificationBell() {
  const [open, setOpen] = useState(false)
  const dispatch = useDispatch()
  const { openAlerts, count } = useSelector(state => state.alerts)
  const { data } = useGetAlertsQuery('open')
  const [ackAlert] = useAckAlertMutation()

  useAlertStream()

  useEffect(() => {
    if (data) dispatch(seedAlerts(data))
  }, [data, dispatch])

  async function handleAck(id) {
    await ackAlert(id)
    dispatch(removeAlert(id))
  }

  return (
    <div className="bell-wrap">
      <button className="bell-btn" onClick={() => setOpen(o => !o)}>
        🔔
        {count > 0 && <span className="bell-badge">{count}</span>}
      </button>
      {open && (
        <div className="bell-dropdown">
          {openAlerts.length === 0
            ? <p className="bell-empty">No open alerts</p>
            : openAlerts.slice(0, 10).map(a => (
              <div key={a.id} className="bell-row">
                <div className="bell-row-info">
                  <p className="bell-rule-name">{a.ruleName}</p>
                  <p className="bell-meta">{new Date(a.triggeredAt).toLocaleString()}</p>
                </div>
                <button className="bell-ack" onClick={() => handleAck(a.id)}>Ack</button>
              </div>
            ))
          }
        </div>
      )}
    </div>
  )
}
