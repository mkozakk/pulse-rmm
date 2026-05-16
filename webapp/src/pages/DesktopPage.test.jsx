import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { Provider } from 'react-redux'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { configureStore } from '@reduxjs/toolkit'
import authReducer from '../store/authSlice'
import DesktopPage from './DesktopPage'

vi.mock('../hooks/useDesktopSession')
import { useDesktopSession } from '../hooks/useDesktopSession'

function mockSession(overrides = {}) {
  useDesktopSession.mockReturnValue({
    videoRef: { current: null },
    status: 'idle',
    canControl: false,
    error: null,
    sendFile: vi.fn(),
    requestDownload: vi.fn(),
    endSession: vi.fn(),
    ...overrides
  })
}

function renderPage() {
  const store = configureStore({
    reducer: { auth: authReducer },
    preloadedState: { auth: { token: 'test-token', initialized: true } }
  })
  return render(
    <Provider store={store}>
      <MemoryRouter initialEntries={['/endpoints/abc-123/desktop']}>
        <Routes>
          <Route path="/endpoints/:id/desktop" element={<DesktopPage />} />
          <Route path="/endpoints/:id" element={<div>detail</div>} />
        </Routes>
      </MemoryRouter>
    </Provider>
  )
}

describe('DesktopPage', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('renders loading state while connecting', () => {
    mockSession({ status: 'connecting' })
    renderPage()
    expect(screen.getByText(/connecting/i)).toBeInTheDocument()
  })

  it('renders the shell heading', () => {
    mockSession({ status: 'idle' })
    renderPage()
    expect(screen.getByText(/desktop — abc-123/i)).toBeInTheDocument()
  })

  it('renders video element', () => {
    mockSession({ status: 'connected', canControl: true })
    renderPage()
    expect(screen.getByTestId('desktop-video')).toBeInTheDocument()
  })

  it('shows view only badge when canControl is false', () => {
    mockSession({ status: 'connected', canControl: false })
    renderPage()
    expect(screen.getByText(/view only/i)).toBeInTheDocument()
  })

  it('shows wayland error message', () => {
    mockSession({ status: 'error', error: 'wayland_not_supported' })
    renderPage()
    expect(screen.getByText(/wayland session without portal/i)).toBeInTheDocument()
  })

  it('shows consent denied message', () => {
    mockSession({ status: 'error', error: 'wayland_consent_denied' })
    renderPage()
    expect(screen.getByText(/declined the screen-share prompt/i)).toBeInTheDocument()
  })

  it('shows portal missing message', () => {
    mockSession({ status: 'error', error: 'wayland_portal_missing' })
    renderPage()
    expect(screen.getByText(/xdg-desktop-portal/i)).toBeInTheDocument()
  })

  it('falls back to generic message for unknown error codes', () => {
    mockSession({ status: 'error', error: 'something_else' })
    renderPage()
    expect(screen.getByText(/something_else/)).toBeInTheDocument()
  })
})
