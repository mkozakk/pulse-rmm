import { Link, useNavigate } from 'react-router-dom'
import { useDispatch } from 'react-redux'
import { useGetEndpointsQuery, useLogoutMutation } from '../api/pulseApi'
import { clearCredentials } from '../store/authSlice'

export default function EndpointsPage() {
  const { data: endpoints = [], isLoading, isError } = useGetEndpointsQuery(undefined, {
    pollingInterval: 30000
  })
  const [logout] = useLogoutMutation()
  const dispatch = useDispatch()
  const navigate = useNavigate()

  async function handleLogout() {
    await logout()
    dispatch(clearCredentials())
    navigate('/login')
  }

  return (
    <div className="page">
      <header className="page-header">
        <h1>Endpoints</h1>
        <button onClick={handleLogout}>Log out</button>
      </header>

      {isLoading && <p>Loading…</p>}
      {isError && <p className="error">Failed to load endpoints.</p>}

      {!isLoading && !isError && (
        <table className="table">
          <thead>
            <tr>
              <th>Hostname</th>
              <th>OS</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {endpoints.map(ep => (
              <tr key={ep.id}>
                <td><Link to={`/endpoints/${ep.id}`}>{ep.hostname}</Link></td>
                <td>{ep.os}</td>
                <td><span className={`badge badge-${ep.status}`}>{ep.status}</span></td>
              </tr>
            ))}
            {endpoints.length === 0 && (
              <tr><td colSpan={3}>No endpoints enrolled.</td></tr>
            )}
          </tbody>
        </table>
      )}
    </div>
  )
}
