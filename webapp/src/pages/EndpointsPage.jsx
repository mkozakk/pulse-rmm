import { Link } from 'react-router-dom'
import { useGetEndpointsQuery } from '../api/pulseApi'
import keycloak from '../keycloak'
import AppShell from '../components/AppShell'

export default function EndpointsPage() {
  const { data: endpoints = [], isLoading, isError } = useGetEndpointsQuery(undefined, {
    pollingInterval: 30000
  })

  function handleLogout() {
    keycloak.logout({ redirectUri: window.location.origin })
  }

  return (
    <AppShell
      title="Endpoints"
      subtitle="Keep an eye on online status and jump into an endpoint's details."
      actions={<button onClick={handleLogout}>Log out</button>}
    >
      {isLoading && <p className="panel-empty">Loading endpoints...</p>}
      {isError && <p className="error">Failed to load endpoints.</p>}

      {!isLoading && !isError && (
        <section className="panel-card">
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
                  <td>
                    <Link to={`/endpoints/${ep.id}`}>{ep.hostname}</Link>
                    <div className="table-secondary">{ep.id}</div>
                  </td>
                  <td>{ep.os}</td>
                  <td><span className={`badge badge-${ep.status}`}>{ep.status}</span></td>
                </tr>
              ))}
              {endpoints.length === 0 && (
                <tr><td colSpan={3}>No endpoints enrolled.</td></tr>
              )}
            </tbody>
          </table>
          <p className="panel-empty">Auto-refreshes every 30 seconds.</p>
        </section>
      )}
    </AppShell>
  )
}
