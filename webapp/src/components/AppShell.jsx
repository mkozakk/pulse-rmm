import { NavLink } from 'react-router-dom'
import {
  Activity,
  Monitor,
  UserPlus,
  FileCode,
  Package,
  Bell,
  ScrollText,
  HardDrive,
  Zap,
  Users,
  Building2
} from 'lucide-react'
import NotificationBell from './NotificationBell'
import keycloak from '../keycloak'

const NAV = [
  {
    label: 'Fleet',
    items: [
      { to: '/endpoints', label: 'Endpoints', Icon: Monitor },
      { to: '/enrolment', label: 'Enrolment', Icon: UserPlus },
    ]
  },
  {
    label: 'Automation',
    items: [
      { to: '/scripts', label: 'Scripts', Icon: FileCode },
      { to: '/software', label: 'Software', Icon: Package },
    ]
  },
  {
    label: 'Monitoring',
    items: [
      { to: '/alerts', label: 'Alerts', Icon: Bell },
      { to: '/audit', label: 'Audit Log', Icon: ScrollText },
    ]
  },
  {
    label: 'Administration',
    items: [
      { to: '/agent-versions', label: 'Agent Versions', Icon: HardDrive },
      { to: '/webhooks', label: 'Webhooks', Icon: Zap },
      { to: '/users', label: 'Users', Icon: Users },
    ]
  }
]

export default function AppShell({ children, title, subtitle, actions }) {
  const isGlobalAdmin = !keycloak.tokenParsed?.org_id

  const adminSection = NAV.find(s => s.label === 'Administration')
  if (isGlobalAdmin && !adminSection.items.find(i => i.to === '/organizations')) {
    adminSection.items.push({ to: '/organizations', label: 'Organizations', Icon: Building2 })
  }

  return (
    <div className="app-shell">
      <aside className="app-shell-nav">
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <Activity size={20} color="#3b82f6" />
          <div>
            <p className="app-shell-brand">Pulse RMM</p>
            <p className="app-shell-tagline">Fleet control center</p>
          </div>
        </div>

        <nav className="app-shell-links">
          {NAV.map(section => (
            <div key={section.label}>
              <p className="nav-section-label">{section.label}</p>
              {section.items.map(({ to, label, Icon }) => (
                <NavLink
                  key={to}
                  to={to}
                  className={({ isActive }) => isActive ? 'app-shell-link active' : 'app-shell-link'}
                >
                  <Icon size={16} />
                  {label}
                </NavLink>
              ))}
            </div>
          ))}
        </nav>
      </aside>

      <main className="app-shell-main">
        <header className="app-shell-header">
          <div>
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
