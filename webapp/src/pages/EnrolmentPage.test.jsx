import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { configureStore } from '@reduxjs/toolkit'
import authReducer from '../store/authSlice'
import { pulseApi } from '../api/pulseApi'
import EnrolmentPage from './EnrolmentPage'

vi.mock('../api/pulseApi', async () => {
  const actual = await vi.importActual('../api/pulseApi')
  return {
    ...actual,
    useGetEndpointsQuery: () => ({ data: [], isLoading: false, isError: false }),
    useGetGroupsQuery: () => ({ data: [], isLoading: false, isError: false }),
    useGetTagRulesQuery: () => ({ data: [], isLoading: false, isError: false }),
    useCreateEnrolmentTokenMutation: () => [vi.fn()],
    useCreateGroupMutation: () => [vi.fn()],
    useCreateTagRuleMutation: () => [vi.fn()],
    useEvaluateTagRulesMutation: () => [vi.fn()],
    useUpdateEndpointGroupMutation: () => [vi.fn()],
    useUpdateEndpointTagsMutation: () => [vi.fn()]
  }
})

function renderPage() {
  const store = configureStore({
    reducer: { auth: authReducer, [pulseApi.reducerPath]: pulseApi.reducer },
    preloadedState: { auth: { token: 'tok', initialized: true } },
    middleware: (getDefaultMiddleware) => getDefaultMiddleware().concat(pulseApi.middleware)
  })

  return render(
    <Provider store={store}>
      <MemoryRouter initialEntries={['/enrolment']}>
        <EnrolmentPage />
      </MemoryRouter>
    </Provider>
  )
}

describe('EnrolmentPage', () => {
  it('renders enrolment workspace', () => {
    renderPage()
    expect(screen.getByText(/create enrolment token/i)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /groups/i })).toBeInTheDocument()
  })
})
