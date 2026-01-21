import { useEffect } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useDispatch } from 'react-redux'
import { useRefreshMutation } from './api/pulseApi'
import { setCredentials, setInitialized } from './store/authSlice'
import ProtectedRoute from './components/ProtectedRoute'
import LoginPage from './pages/LoginPage'
import EndpointsPage from './pages/EndpointsPage'
import EndpointDetailPage from './pages/EndpointDetailPage'

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
          <Route element={<ProtectedRoute />}>
            <Route path="/endpoints" element={<EndpointsPage />} />
            <Route path="/endpoints/:id" element={<EndpointDetailPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/endpoints" replace />} />
        </Routes>
      </Bootstrap>
    </BrowserRouter>
  )
}
