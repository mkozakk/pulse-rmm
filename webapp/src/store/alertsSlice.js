import { createSlice } from '@reduxjs/toolkit'

const alertsSlice = createSlice({
  name: 'alerts',
  initialState: { openAlerts: [], count: 0, notifs: [] },
  reducers: {
    seedAlerts: (state, action) => {
      state.openAlerts = action.payload
      state.count = action.payload.length + state.notifs.length
    },
    addAlert: (state, action) => {
      state.openAlerts.unshift(action.payload)
      state.count += 1
    },
    removeAlert: (state, action) => {
      state.openAlerts = state.openAlerts.filter(a => a.id !== action.payload)
      state.count = state.openAlerts.length + state.notifs.length
    },
    addNotif: (state, action) => {
      state.notifs.unshift(action.payload)
      state.count += 1
    },
    dismissNotif: (state, action) => {
      state.notifs = state.notifs.filter((_, i) => i !== action.payload)
      state.count = state.openAlerts.length + state.notifs.length
    }
  }
})

export const { seedAlerts, addAlert, removeAlert, addNotif, dismissNotif } = alertsSlice.actions
export default alertsSlice.reducer
