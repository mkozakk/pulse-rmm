import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { configureStore } from '@reduxjs/toolkit'
import authReducer from '../store/authSlice'
import LoginPage from './LoginPage'

const loginMock = vi.fn()

vi.mock('../api/pulseApi', () => ({
  useLoginMutation: () => [loginMock, { isLoading: false }]
}))

function renderPage() {
  const store = configureStore({
    reducer: { auth: authReducer },
    preloadedState: { auth: { token: null, initialized: true } }
  })

  return render(
    <Provider store={store}>
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    </Provider>
  )
}

describe('LoginPage', () => {
  it('renders register link', () => {
    renderPage()
    expect(screen.getByText(/register/i)).toBeInTheDocument()
  })

  it('submits credentials', async () => {
    loginMock.mockReturnValueOnce({ unwrap: () => Promise.resolve({ accessToken: 'tok123' }) })
    renderPage()

    fireEvent.change(screen.getByPlaceholderText(/username/i), { target: { value: 'admin' } })
    fireEvent.change(screen.getByPlaceholderText(/password/i), { target: { value: 'secret12345' } })
    fireEvent.submit(screen.getByRole('button', { name: /log in/i }))

    expect(loginMock).toHaveBeenCalledWith({ username: 'admin', password: 'secret12345' })
  })
})
