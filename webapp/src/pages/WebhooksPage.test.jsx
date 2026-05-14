import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { configureStore } from '@reduxjs/toolkit'
import authReducer from '../store/authSlice'
import alertsReducer from '../store/alertsSlice'
import { pulseApi } from '../api/pulseApi'
import WebhooksPage from './WebhooksPage'

const sampleWebhook = {
  id: 'wh-1',
  url: 'https://example.com/hook',
  eventTypes: ['alert.fired', 'endpoint.enrolled'],
  enabled: true,
  createdAt: '2026-05-01T10:00:00Z'
}

vi.mock('../components/NotificationBell', () => ({ default: () => null }))

const mockCreate = vi.fn(() => ({ unwrap: () => Promise.resolve() }))
const mockDelete = vi.fn(() => ({ unwrap: () => Promise.resolve() }))

vi.mock('../api/pulseApi', async () => {
  const actual = await vi.importActual('../api/pulseApi')
  return {
    ...actual,
    useListWebhooksQuery: () => ({ data: [sampleWebhook], refetch: vi.fn() }),
    useCreateWebhookMutation: () => [mockCreate],
    useUpdateWebhookMutation: () => [vi.fn(() => ({ unwrap: () => Promise.resolve() }))],
    useDeleteWebhookMutation: () => [mockDelete]
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
      <MemoryRouter initialEntries={['/webhooks']}>
        <WebhooksPage />
      </MemoryRouter>
    </Provider>
  )
}

describe('WebhooksPage', () => {
  it('renders webhook list', () => {
    renderPage()
    expect(screen.getByText('https://example.com/hook')).toBeInTheDocument()
    expect(screen.getByText(/alert\.fired/)).toBeInTheDocument()
  })

  it('shows add form when button clicked', () => {
    renderPage()
    fireEvent.click(screen.getByText('+ Add Webhook'))
    expect(screen.getByPlaceholderText(/URL/i)).toBeInTheDocument()
    expect(screen.getByPlaceholderText(/Secret/i)).toBeInTheDocument()
  })

  it('submits create with correct payload', async () => {
    renderPage()
    fireEvent.click(screen.getByText('+ Add Webhook'))

    fireEvent.change(screen.getByPlaceholderText(/URL/i), { target: { value: 'https://hooks.example.com' } })
    fireEvent.change(screen.getByPlaceholderText(/Secret/i), { target: { value: 'supersecretvalue123' } })

    const checkboxes = screen.getAllByRole('checkbox')
    fireEvent.click(checkboxes[0])

    fireEvent.click(screen.getByRole('button', { name: 'Add Webhook' }))

    await waitFor(() => {
      expect(mockCreate).toHaveBeenCalledWith(expect.objectContaining({
        url: 'https://hooks.example.com',
        secret: 'supersecretvalue123'
      }))
    })
  })

  it('confirms before deleting', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderPage()
    fireEvent.click(screen.getByText('Delete'))
    expect(window.confirm).toHaveBeenCalled()
    await waitFor(() => expect(mockDelete).toHaveBeenCalledWith('wh-1'))
  })
})
