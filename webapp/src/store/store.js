import { configureStore } from '@reduxjs/toolkit'
import { pulseApi } from '../api/pulseApi'
import authReducer from './authSlice'

export const store = configureStore({
  reducer: {
    auth: authReducer,
    [pulseApi.reducerPath]: pulseApi.reducer
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware().concat(pulseApi.middleware)
})
