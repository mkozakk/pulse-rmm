import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { vi } from 'vitest'
import authReducer from '../store/authSlice'
import ProtectedRoute from './ProtectedRoute'
import keycloak from '../keycloak'

vi.mock('../keycloak', () => ({ default: { login: vi.fn() } }))

function makeStore(token) {
  return configureStore({
    reducer: { auth: authReducer },
    preloadedState: { auth: { token, initialized: true } }
  })
}

it('renders children when authenticated', () => {
  render(
    <Provider store={makeStore('mytoken')}>
      <MemoryRouter>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path="/" element={<div>protected content</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </Provider>
  )
  expect(screen.getByText('protected content')).toBeInTheDocument()
})

it('triggers Keycloak login when not authenticated', () => {
  render(
    <Provider store={makeStore(null)}>
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path="/" element={<div>protected content</div>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </Provider>
  )
  expect(keycloak.login).toHaveBeenCalled()
  expect(screen.queryByText('protected content')).not.toBeInTheDocument()
})
