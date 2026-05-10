import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { configureStore } from '@reduxjs/toolkit'
import authReducer from '../store/authSlice'
import RegisterPage from './RegisterPage'

const registerMock = vi.fn()
const loginMock = vi.fn()

vi.mock('../api/pulseApi', () => ({
  useRegisterMutation: () => [registerMock, { isLoading: false }],
  useLoginMutation: () => [loginMock]
}))

function renderPage() {
  const store = configureStore({
    reducer: { auth: authReducer },
    preloadedState: { auth: { token: null, initialized: true } }
  })

  return render(
    <Provider store={store}>
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>
    </Provider>
  )
}

describe('RegisterPage', () => {
  it('renders login link', () => {
    renderPage()
    expect(screen.getByText(/log in/i)).toBeInTheDocument()
  })

  it('submits registration data', () => {
    registerMock.mockReturnValueOnce({ unwrap: () => Promise.resolve({ id: '1' }) })
    loginMock.mockReturnValueOnce({ unwrap: () => Promise.resolve({ accessToken: 'tok123' }) })
    renderPage()

    fireEvent.change(screen.getByPlaceholderText(/username/i), { target: { value: 'admin' } })
    fireEvent.change(screen.getByPlaceholderText(/password/i), { target: { value: 'secret12345' } })
    fireEvent.submit(screen.getByRole('button', { name: /register/i }))

    expect(registerMock).toHaveBeenCalledWith({ username: 'admin', password: 'secret12345' })
  })
})
