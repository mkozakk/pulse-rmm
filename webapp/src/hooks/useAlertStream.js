import { useEffect } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { addAlert } from '../store/alertsSlice'

const BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080/api'

export function useAlertStream() {
  const dispatch = useDispatch()
  const token = useSelector(state => state.auth.token)

  useEffect(() => {
    if (!token) return

    const url = `${BASE}/alerts/stream?token=${encodeURIComponent(token)}`
    const es = new EventSource(url)

    es.addEventListener('alert', e => {
      dispatch(addAlert(JSON.parse(e.data)))
    })

    return () => es.close()
  }, [token, dispatch])
}
