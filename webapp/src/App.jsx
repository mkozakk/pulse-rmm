import { useEffect } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useDispatch } from 'react-redux'
import { useRefreshMutation } from './api/pulseApi'
import { setCredentials, setInitialized } from './store/authSlice'
import ProtectedRoute from './components/ProtectedRoute'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import EnrolmentPage from './pages/EnrolmentPage'
import ScriptsPage from './pages/ScriptsPage'
import SoftwarePage from './pages/SoftwarePage'
import EndpointsPage from './pages/EndpointsPage'
import EndpointDetailPage from './pages/EndpointDetailPage'
import TerminalPage from './pages/TerminalPage'
import DesktopPage from './pages/DesktopPage'
import AlertsPage from './pages/AlertsPage'
import AuditPage from './pages/AuditPage'

function Bootstrap({ children }) {
  const dispatch = useDispatch()
  const [refresh] = useRefreshMutation()

  useEffect(() => {
    refresh()
      .unwrap()
      .then(data => dispatch(setCredentials(data.accessToken)))
      .catch(() => {})
      .finally(() => dispatch(setInitialized()))
  }, [])

  return children
}

export default function App() {
  return (
    <BrowserRouter>
      <Bootstrap>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route element={<ProtectedRoute />}> 
            <Route path="/enrolment" element={<EnrolmentPage />} />
            <Route path="/scripts" element={<ScriptsPage />} />
            <Route path="/software" element={<SoftwarePage />} />
            <Route path="/endpoints" element={<EndpointsPage />} />
            <Route path="/endpoints/:id" element={<EndpointDetailPage />} />
            <Route path="/endpoints/:id/shell" element={<TerminalPage />} />
            <Route path="/endpoints/:id/desktop" element={<DesktopPage />} />
            <Route path="/alerts" element={<AlertsPage />} />
            <Route path="/audit" element={<AuditPage />} />
          </Route>
          <Route path="/" element={<Navigate to="/endpoints" replace />} />
          <Route path="*" element={<Navigate to="/endpoints" replace />} />
        </Routes>
      </Bootstrap>
    </BrowserRouter>
  )
}
