import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { configureStore } from '@reduxjs/toolkit'
import authReducer from '../store/authSlice'
import alertsReducer from '../store/alertsSlice'
import { pulseApi } from '../api/pulseApi'
import UsersPage from './UsersPage'

const sampleUser = {
  id: 'aaaa-1111',
  username: 'alice',
  email: 'alice@test.com',
  firstName: 'Alice',
  lastName: 'Smith',
  enabled: true,
  roles: ['Admin']
}

vi.mock('../components/NotificationBell', () => ({ default: () => null }))

vi.mock('../api/pulseApi', async () => {
  const actual = await vi.importActual('../api/pulseApi')
  return {
    ...actual,
    useGetUsersQuery: () => ({ data: [sampleUser], isLoading: false }),
    useGetRolesQuery: () => ({ data: [{ id: 'r1', name: 'Admin' }] }),
    useCreateUserMutation: () => [vi.fn()],
    useUpdateUserMutation: () => [vi.fn()],
    useDeleteUserMutation: () => [vi.fn()],
    useUpdateUserRolesMutation: () => [vi.fn()]
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
      <MemoryRouter initialEntries={['/users']}>
        <UsersPage />
      </MemoryRouter>
    </Provider>
  )
}

describe('UsersPage', () => {
  it('renders user row', () => {
    renderPage()
    expect(screen.getByText('alice')).toBeInTheDocument()
    expect(screen.getByText('alice@test.com')).toBeInTheDocument()
    expect(screen.getByText('Active')).toBeInTheDocument()
    expect(screen.getByText('Admin')).toBeInTheDocument()
  })

  it('shows New User button', () => {
    renderPage()
    expect(screen.getByRole('button', { name: /new user/i })).toBeInTheDocument()
  })
})
