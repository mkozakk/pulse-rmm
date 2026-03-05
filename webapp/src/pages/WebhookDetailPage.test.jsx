import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { Provider } from 'react-redux'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { configureStore } from '@reduxjs/toolkit'
import authReducer from '../store/authSlice'
import alertsReducer from '../store/alertsSlice'
import { pulseApi } from '../api/pulseApi'
import WebhookDetailPage from './WebhookDetailPage'

const sampleWebhook = {
  id: 'wh-1',
  url: 'https://example.com/hook',
  eventTypes: ['audit.*'],
  enabled: true,
  createdAt: '2026-05-01T10:00:00Z'
}

const sampleDeliveries = [
  { id: 'd-1', eventType: 'audit.alert_rule.create', status: 'success', attempts: 1, lastStatusCode: 200, createdAt: '2026-05-01T10:01:00Z' },
  { id: 'd-2', eventType: 'audit.alert_rule.delete', status: 'dead_letter', attempts: 3, lastStatusCode: 500, createdAt: '2026-05-01T10:02:00Z' },
  { id: 'd-3', eventType: 'audit.alert_rule.create', status: 'retrying', attempts: 1, lastStatusCode: null, createdAt: '2026-05-01T10:03:00Z' }
]

vi.mock('../components/NotificationBell', () => ({ default: () => null }))

let statusFilter = ''
vi.mock('../api/pulseApi', async () => {
  const actual = await vi.importActual('../api/pulseApi')
  return {
    ...actual,
    useListWebhooksQuery: () => ({ data: [sampleWebhook] }),
    useListDeliveriesQuery: ({ status }) => ({
      data: status ? sampleDeliveries.filter(d => d.status === status) : sampleDeliveries,
      isLoading: false
    }),
    useGetDeliveryQuery: () => ({ data: null, isLoading: false })
  }
})

function renderPage() {
  const store = configureStore({
    reducer: { auth: authReducer, alerts: alertsReducer, [pulseApi.reducerPath]: pulseApi.reducer },
    preloadedState: { auth: { token: 'tok', initialized: true } },
    middleware: (m) => m().concat(pulseApi.middleware)
  })
  return render(
    <Provider store={store}>
      <MemoryRouter initialEntries={['/webhooks/wh-1']}>
        <Routes>
          <Route path="/webhooks/:id" element={<WebhookDetailPage />} />
        </Routes>
      </MemoryRouter>
    </Provider>
  )
}

describe('WebhookDetailPage', () => {
  it('renders delivery rows', () => {
    renderPage()
    expect(screen.getAllByText('audit.alert_rule.create').length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText('dead_letter')).toBeInTheDocument()
    expect(screen.getByText('retrying')).toBeInTheDocument()
  })

  it('status filter narrows list', () => {
    renderPage()
    const select = screen.getByRole('combobox')
    fireEvent.change(select, { target: { value: 'success' } })
    expect(screen.getAllByText('audit.alert_rule.create')).toHaveLength(1)
    expect(screen.queryByText('dead_letter')).not.toBeInTheDocument()
  })

  it('shows webhook url in summary', () => {
    renderPage()
    expect(screen.getAllByText('https://example.com/hook').length).toBeGreaterThanOrEqual(1)
  })
})
