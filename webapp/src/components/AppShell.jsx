import { NavLink } from 'react-router-dom'
import NotificationBell from './NotificationBell'

export default function AppShell({ children, title, subtitle, actions }) {
  return (
    <div className="app-shell">
      <aside className="app-shell-nav">
        <div>
          <p className="app-shell-brand">Pulse RMM</p>
          <p className="app-shell-tagline">Fleet control center</p>
        </div>

        <nav className="app-shell-links">
          <NavLink to="/enrolment" className={({ isActive }) => isActive ? 'app-shell-link active' : 'app-shell-link'}>
            Enrolment
          </NavLink>
          <NavLink to="/scripts" className={({ isActive }) => isActive ? 'app-shell-link active' : 'app-shell-link'}>
            Scripts
          </NavLink>
          <NavLink to="/software" className={({ isActive }) => isActive ? 'app-shell-link active' : 'app-shell-link'}>
            Software
          </NavLink>
          <NavLink to="/endpoints" className={({ isActive }) => isActive ? 'app-shell-link active' : 'app-shell-link'}>
            Endpoints
          </NavLink>
          <NavLink to="/alerts" className={({ isActive }) => isActive ? 'app-shell-link active' : 'app-shell-link'}>
            Alerts
          </NavLink>
          <NavLink to="/audit" className={({ isActive }) => isActive ? 'app-shell-link active' : 'app-shell-link'}>
            Audit Log
          </NavLink>
          <NavLink to="/agent-versions" className={({ isActive }) => isActive ? 'app-shell-link active' : 'app-shell-link'}>
            Agent Versions
          </NavLink>
          <NavLink to="/webhooks" className={({ isActive }) => isActive ? 'app-shell-link active' : 'app-shell-link'}>
            Webhooks
          </NavLink>
        </nav>

        <p className="app-shell-note">Metrics, shell, and desktop live here.</p>
      </aside>

      <main className="app-shell-main">
        <header className="app-shell-header">
          <div>
            <p className="app-shell-kicker">Webapp</p>
            <h1>{title}</h1>
            {subtitle && <p className="app-shell-subtitle">{subtitle}</p>}
          </div>
          <div className="app-shell-actions">
            {actions}
            <NotificationBell />
          </div>
        </header>

        {children}
      </main>
    </div>
  )
}
