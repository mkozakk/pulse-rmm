import { Component } from 'react'

export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props)
    this.state = { error: null }
  }

  static getDerivedStateFromError(error) {
    return { error }
  }

  render() {
    if (this.state.error) {
      return (
        <div style={{ padding: '4rem 2rem', maxWidth: 480, margin: '0 auto', textAlign: 'center' }}>
          <h2 style={{ fontSize: '1.25rem' }}>Something went wrong</h2>
          <p style={{ color: '#64748b', marginTop: '0.5rem', fontSize: '0.875rem' }}>
            {this.state.error.message}
          </p>
          <button
            onClick={() => window.location.reload()}
            style={{ marginTop: '1.5rem', padding: '0.5rem 1.25rem', borderRadius: 8, border: '1px solid #d1d5db', background: '#fff', cursor: 'pointer' }}
          >
            Reload page
          </button>
        </div>
      )
    }
    return this.props.children
  }
}
