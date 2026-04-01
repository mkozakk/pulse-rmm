import { useSelector } from 'react-redux'
import { Outlet } from 'react-router-dom'
import keycloak from '../keycloak'

export default function ProtectedRoute() {
  const token = useSelector(state => state.auth.token)
  const initialized = useSelector(state => state.auth.initialized)

  if (!initialized) return null
  if (!token) {
    keycloak.login()
    return null
  }
  return <Outlet />
}
