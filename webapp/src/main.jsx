import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { Provider } from 'react-redux'
import { store } from './store/store'
import { setCredentials, clearCredentials, setInitialized } from './store/authSlice'
import keycloak from './keycloak'
import App from './App.jsx'
import ErrorBoundary from './components/ErrorBoundary.jsx'
import './index.css'
import '@xterm/xterm/css/xterm.css'

const root = createRoot(document.getElementById('root'))

root.render(
  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh', color: '#64748b', fontSize: 14 }}>
    Initialising…
  </div>
)

keycloak
  .init({ onLoad: 'login-required', pkceMethod: 'S256', checkLoginIframe: false })
  .then((authenticated) => {
    if (authenticated) store.dispatch(setCredentials(keycloak.token))
    store.dispatch(setInitialized())

    keycloak.onAuthRefreshSuccess = () => store.dispatch(setCredentials(keycloak.token))
    keycloak.onAuthLogout = () => store.dispatch(clearCredentials())
    keycloak.onTokenExpired = () => keycloak.updateToken(30).catch(() => keycloak.login())

    root.render(
      <StrictMode>
        <Provider store={store}>
          <ErrorBoundary>
            <App />
          </ErrorBoundary>
        </Provider>
      </StrictMode>
    )
  })
