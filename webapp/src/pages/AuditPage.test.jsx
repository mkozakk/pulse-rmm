import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { configureStore } from '@reduxjs/toolkit'
import authReducer from '../store/authSlice'
import alertsReducer from '../store/alertsSlice'
import { pulseApi } from '../api/pulseApi'
import AuditPage from './AuditPage'

const sampleEvent = {
  id: 'ev-1',
  userId: 'uid-1111',
  username: 'alice',
  action: 'script.run',
  permissionUsed: 'SCRIPTS_RUN',
  endpointId: 'ep-2222',
  payload: { scriptId: 's1' },
  createdAt: '2026-05-01T10:00:00Z'
}

vi.mock('../components/NotificationBell', () => ({ default: () => null }))

vi.mock('../api/pulseApi', async () => {
  const actual = await vi.importActual('../api/pulseApi')
  return {
    ...actual,
    useGetAlertsQuery: () => ({ data: [] }),
    useGetAuditLogQuery: () => ({
      data: { content: [sampleEvent], totalPages: 1 },
      isLoading: false,
      isError: false
    })
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
      <MemoryRouter initialEntries={['/audit']}>
        <AuditPage />
      </MemoryRouter>
    </Provider>
  )
}

describe('AuditPage', () => {
  it('renders table with event row', () => {
    renderPage()
    expect(screen.getByText('script.run')).toBeInTheDocument()
    expect(screen.getByText('alice')).toBeInTheDocument()
    expect(screen.getByText('SCRIPTS_RUN')).toBeInTheDocument()
  })

  it('export buttons have correct query params', () => {
    renderPage()
    const csvLink = screen.getByRole('link', { name: /export csv/i })
    expect(csvLink.getAttribute('href')).toContain('format=csv')
    const jsonLink = screen.getByRole('link', { name: /export json/i })
    expect(jsonLink.getAttribute('href')).toContain('format=json')
  })
})
