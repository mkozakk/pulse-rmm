import { render, screen, act } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { Provider } from 'react-redux'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { configureStore } from '@reduxjs/toolkit'
import authReducer from '../store/authSlice'
import { Terminal } from '@xterm/xterm'
import { FitAddon } from '@xterm/addon-fit'
import TerminalPage from './TerminalPage'

vi.mock('@xterm/xterm', () => ({ Terminal: vi.fn() }))
vi.mock('@xterm/addon-fit', () => ({ FitAddon: vi.fn() }))

let mockWs

beforeEach(() => {
  vi.clearAllMocks()

  Terminal.mockImplementation(function () {
    this.open = vi.fn()
    this.write = vi.fn()
    this.writeln = vi.fn()
    this.onData = vi.fn()
    this.loadAddon = vi.fn()
    this.dispose = vi.fn()
  })

  FitAddon.mockImplementation(function () {
    this.fit = vi.fn()
    this.proposeDimensions = vi.fn(() => ({ cols: 80, rows: 24 }))
  })

  mockWs = {
    send: vi.fn(),
    close: vi.fn(),
    readyState: 1,
    binaryType: null,
    onmessage: null,
    onclose: null,
  }
  function MockWebSocket() { return mockWs }
  MockWebSocket.OPEN = 1
  vi.stubGlobal('WebSocket', MockWebSocket)
})

function renderPage() {
  const store = configureStore({
    reducer: { auth: authReducer },
    preloadedState: { auth: { token: 'test-token', initialized: true } },
  })
  return render(
    <Provider store={store}>
      <MemoryRouter initialEntries={['/endpoints/abc-123/shell']}>
        <Routes>
          <Route path="/endpoints/:id/shell" element={<TerminalPage />} />
          <Route path="/endpoints/:id" element={<div>detail</div>} />
        </Routes>
      </MemoryRouter>
    </Provider>
  )
}

describe('TerminalPage', () => {
  it('mounts xterm container', () => {
    renderPage()
    expect(screen.getByRole('region', { name: /terminal/i })).toBeInTheDocument()
  })

  it('writes output when WS sends 0x01 frame', () => {
    renderPage()
    const term = Terminal.mock.instances[0]

    const data = new Uint8Array([0x01, 104, 101, 108, 108, 111]) // 0x01 + "hello"
    act(() => { mockWs.onmessage({ data: data.buffer }) })

    expect(term.write).toHaveBeenCalledWith(expect.any(Uint8Array))
  })

  it('sends keystrokes as 0x01 binary frame', () => {
    renderPage()
    const term = Terminal.mock.instances[0]
    const onDataCb = term.onData.mock.calls[0][0]

    act(() => { onDataCb('ls\n') })

    expect(mockWs.send).toHaveBeenCalled()
    const frame = new Uint8Array(mockWs.send.mock.calls[0][0])
    expect(frame[0]).toBe(0x01)
    expect(new TextDecoder().decode(frame.slice(1))).toBe('ls\n')
  })

  it('shows session closed message on WS close', () => {
    renderPage()

    act(() => { mockWs.onclose() })

    expect(screen.getByText(/session closed/i)).toBeInTheDocument()
  })
})
