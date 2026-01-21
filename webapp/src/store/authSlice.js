import { createSlice } from '@reduxjs/toolkit'

const authSlice = createSlice({
  name: 'auth',
  initialState: { token: null, initialized: false },
  reducers: {
    setCredentials: (state, action) => { state.token = action.payload },
    clearCredentials: (state) => { state.token = null },
    setInitialized: (state) => { state.initialized = true }
  }
})

export const { setCredentials, clearCredentials, setInitialized } = authSlice.actions
export default authSlice.reducer
