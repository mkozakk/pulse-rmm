import { configureStore } from '@reduxjs/toolkit'
import { pulseApi } from '../api/pulseApi'
import authReducer from './authSlice'
import alertsReducer from './alertsSlice'

export const store = configureStore({
  reducer: {
    auth: authReducer,
    alerts: alertsReducer,
    [pulseApi.reducerPath]: pulseApi.reducer
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware().concat(pulseApi.middleware)
})
