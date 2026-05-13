import { createSlice } from '@reduxjs/toolkit'

const alertsSlice = createSlice({
  name: 'alerts',
  initialState: { openAlerts: [], count: 0 },
  reducers: {
    seedAlerts: (state, action) => {
      state.openAlerts = action.payload
      state.count = action.payload.length
    },
    addAlert: (state, action) => {
      state.openAlerts.unshift(action.payload)
      state.count += 1
    },
    removeAlert: (state, action) => {
      state.openAlerts = state.openAlerts.filter(a => a.id !== action.payload)
      state.count = state.openAlerts.length
    }
  }
})

export const { seedAlerts, addAlert, removeAlert } = alertsSlice.actions
export default alertsSlice.reducer
