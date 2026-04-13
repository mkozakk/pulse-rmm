import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { configureStore } from '@reduxjs/toolkit'
import authReducer from '../store/authSlice'
import alertsReducer from '../store/alertsSlice'
import { pulseApi } from '../api/pulseApi'
import ScriptsPage from './ScriptsPage'

vi.mock('../api/pulseApi', async () => {
  const actual = await vi.importActual('../api/pulseApi')
  return {
    ...actual,
    useGetScriptsQuery: () => ({ data: { scripts: [], total: 0 }, isLoading: false, isError: false }),
    useGetEndpointsQuery: () => ({ data: [], isLoading: false, isError: false }),
    useGetScriptRunResultsQuery: () => ({ data: { results: [], total: 0, pending: 0 }, isLoading: false, isError: false }),
    useCreateScriptMutation: () => [vi.fn()],
    useApproveScriptMutation: () => [vi.fn()],
    useRunScriptMutation: () => [vi.fn()],
    useAckScriptExecutionMutation: () => [vi.fn()]
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
      <MemoryRouter initialEntries={['/scripts']}>
        <ScriptsPage />
      </MemoryRouter>
    </Provider>
  )
}

describe('ScriptsPage', () => {
  it('renders scripts workspace', () => {
    renderPage()
    expect(screen.getByText(/upload script/i)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /library/i })).toBeInTheDocument()
  })
})
