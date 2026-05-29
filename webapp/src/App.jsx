import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import ProtectedRoute from './components/ProtectedRoute'
import EnrolmentPage from './pages/EnrolmentPage'
import ScriptsPage from './pages/ScriptsPage'
import SoftwarePage from './pages/SoftwarePage'
import EndpointsPage from './pages/EndpointsPage'
import EndpointDetailPage from './pages/EndpointDetailPage'
import TerminalPage from './pages/TerminalPage'
import DesktopPage from './pages/DesktopPage'
import FilesPage from './pages/FilesPage'
import EndpointProcessesPage from './pages/EndpointProcessesPage'
import AlertsPage from './pages/AlertsPage'
import AuditPage from './pages/AuditPage'
import AgentVersionsPage from './pages/AgentVersionsPage'
import WebhooksPage from './pages/WebhooksPage'
import WebhookDetailPage from './pages/WebhookDetailPage'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<ProtectedRoute />}>
          <Route path="/enrolment" element={<EnrolmentPage />} />
          <Route path="/scripts" element={<ScriptsPage />} />
          <Route path="/software" element={<SoftwarePage />} />
          <Route path="/endpoints" element={<EndpointsPage />} />
          <Route path="/endpoints/:id" element={<EndpointDetailPage />} />
          <Route path="/endpoints/:id/shell" element={<TerminalPage />} />
          <Route path="/endpoints/:id/desktop" element={<DesktopPage />} />
          <Route path="/endpoints/:id/files" element={<FilesPage />} />
          <Route path="/endpoints/:id/processes" element={<EndpointProcessesPage />} />
          <Route path="/alerts" element={<AlertsPage />} />
          <Route path="/audit" element={<AuditPage />} />
          <Route path="/agent-versions" element={<AgentVersionsPage />} />
          <Route path="/webhooks" element={<WebhooksPage />} />
          <Route path="/webhooks/:id" element={<WebhookDetailPage />} />
        </Route>
        <Route path="/" element={<Navigate to="/endpoints" replace />} />
        <Route path="*" element={<Navigate to="/endpoints" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
