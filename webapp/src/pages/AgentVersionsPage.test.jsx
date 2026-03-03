import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { configureStore } from '@reduxjs/toolkit'
import authReducer from '../store/authSlice'
import alertsReducer from '../store/alertsSlice'
import { pulseApi } from '../api/pulseApi'
import AgentVersionsPage from './AgentVersionsPage'

const sampleVersions = [
  { id: 'v1', version: '1.2.3', os: 'linux', arch: 'amd64', current: true, sizeBytes: 5242880, sha256: 'abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890' },
  { id: 'v2', version: '1.2.2', os: 'linux', arch: 'amd64', current: false, sizeBytes: 5000000, sha256: '1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef' }
]

vi.mock('../components/NotificationBell', () => ({ default: () => null }))

vi.mock('../api/pulseApi', async () => {
  const actual = await vi.importActual('../api/pulseApi')
  return {
    ...actual,
    useGetAlertsQuery: () => ({ data: [] }),
    useListAgentVersionsQuery: () => ({ data: sampleVersions, refetch: vi.fn() }),
    usePublishAgentVersionMutation: () => [vi.fn()],
    useSetCurrentAgentVersionMutation: () => [vi.fn()],
    useDeleteAgentVersionMutation: () => [vi.fn()]
  }
})

function renderPage() {
  const store = configureStore({
    reducer: { auth: authReducer, alerts: alertsReducer, [pulseApi.reducerPath]: pulseApi.reducer },
    preloadedState: { auth: { token: 'tok', initialized: true } },
    middleware: (getDefaultMiddleware) => getDefaultMiddleware().concat(pulseApi.middleware)
  })
  return render(
    <Provider store={store}>
      <MemoryRouter initialEntries={['/agent-versions']}>
        <AgentVersionsPage />
      </MemoryRouter>
    </Provider>
  )
}

describe('AgentVersionsPage', () => {
  it('renders version rows grouped by platform', () => {
    renderPage()
    expect(screen.getByText('linux/amd64')).toBeInTheDocument()
    expect(screen.getByText('1.2.3')).toBeInTheDocument()
    expect(screen.getByText('1.2.2')).toBeInTheDocument()
  })

  it('shows current badge on the current version', () => {
    renderPage()
    expect(screen.getByText('current')).toBeInTheDocument()
  })

  it('shows upload form fields', () => {
    renderPage()
    expect(screen.getByPlaceholderText(/version/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /upload/i })).toBeInTheDocument()
  })
})
