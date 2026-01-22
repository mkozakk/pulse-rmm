import { describe, it, expect } from 'vitest'
import authReducer, { setCredentials, clearCredentials } from './authSlice'

describe('authSlice', () => {
  it('setCredentials stores the token', () => {
    const state = authReducer(undefined, setCredentials('tok123'))
    expect(state.token).toBe('tok123')
  })

  it('clearCredentials removes the token', () => {
    const withToken = authReducer(undefined, setCredentials('tok123'))
    const state = authReducer(withToken, clearCredentials())
    expect(state.token).toBeNull()
  })
})
