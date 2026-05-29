import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { Provider } from 'react-redux'
import { store } from './store/store'
import { setCredentials, clearCredentials, setInitialized } from './store/authSlice'
import keycloak from './keycloak'
import App from './App.jsx'
import './index.css'
import '@xterm/xterm/css/xterm.css'

keycloak
  .init({ onLoad: 'login-required', pkceMethod: 'S256' })
  .then((authenticated) => {
    if (authenticated) store.dispatch(setCredentials(keycloak.token))
    store.dispatch(setInitialized())

    // keep the token used for REST (header) and WebSocket (?token=) fresh
    keycloak.onAuthRefreshSuccess = () => store.dispatch(setCredentials(keycloak.token))
    keycloak.onAuthLogout = () => store.dispatch(clearCredentials())
    keycloak.onTokenExpired = () => keycloak.updateToken(30).catch(() => keycloak.login())

    createRoot(document.getElementById('root')).render(
      <StrictMode>
        <Provider store={store}>
          <App />
        </Provider>
      </StrictMode>
    )
  })
