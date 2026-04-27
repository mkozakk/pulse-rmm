import { useSelector } from 'react-redux'
import { Navigate, Outlet } from 'react-router-dom'

export default function ProtectedRoute() {
  const token = useSelector(state => state.auth.token)
  const initialized = useSelector(state => state.auth.initialized)

  if (!initialized) return null
  if (!token) return <Navigate to="/login" replace />
  return <Outlet />
}
