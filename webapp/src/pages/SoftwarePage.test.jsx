import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { configureStore } from '@reduxjs/toolkit'
import authReducer from '../store/authSlice'
import alertsReducer from '../store/alertsSlice'
import { pulseApi } from '../api/pulseApi'
import SoftwarePage from './SoftwarePage'

vi.mock('../api/pulseApi', async () => {
  const actual = await vi.importActual('../api/pulseApi')
  return {
    ...actual,
    useGetEndpointsQuery: () => ({ data: [], isLoading: false, isError: false }),
    useGetSoftwareQuery: () => ({ data: [], isLoading: false, isError: false }),
    useRemoveSoftwareMutation: () => [vi.fn()]
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
      <MemoryRouter initialEntries={['/software']}>
        <SoftwarePage />
      </MemoryRouter>
    </Provider>
  )
}

describe('SoftwarePage', () => {
  it('renders software workspace', () => {
    renderPage()
    expect(screen.getByText(/installed software/i)).toBeInTheDocument()
  })
})
