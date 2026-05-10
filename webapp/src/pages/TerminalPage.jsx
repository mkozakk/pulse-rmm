import { useEffect, useRef, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useSelector } from 'react-redux'
import { Terminal } from '@xterm/xterm'
import { FitAddon } from '@xterm/addon-fit'
import AppShell from '../components/AppShell'

const WS_BASE = import.meta.env.VITE_WS_BASE ?? 'ws://localhost:8080'

export default function TerminalPage() {
  const { id } = useParams()
  const token = useSelector(state => state.auth.token)
  const termRef = useRef(null)
  const wsRef = useRef(null)
  const [closed, setClosed] = useState(false)

  useEffect(() => {
    const term = new Terminal({ cursorBlink: true, convertEol: true })
    const fitAddon = new FitAddon()
    term.loadAddon(fitAddon)
    term.open(termRef.current)
    fitAddon.fit()

    const ws = new WebSocket(`${WS_BASE}/ws/shell/${id}?token=${token}`)
    ws.binaryType = 'arraybuffer'
    wsRef.current = ws

    term.onData(data => {
      if (ws.readyState !== WebSocket.OPEN) return
      const encoded = new TextEncoder().encode(data)
      const frame = new Uint8Array(encoded.length + 1)
      frame[0] = 0x01
      frame.set(encoded, 1)
      ws.send(frame)
    })

    ws.onmessage = e => {
      const data = new Uint8Array(e.data)
      if (data[0] === 0x01) {
        term.write(data.slice(1))
      }
    }

    ws.onclose = () => {
      term.writeln('\r\n[session closed]')
      setClosed(true)
    }

    const handleResize = () => {
      fitAddon.fit()
      const dims = fitAddon.proposeDimensions()
      if (dims && ws.readyState === WebSocket.OPEN) {
        const buf = new ArrayBuffer(5)
        const view = new DataView(buf)
        view.setUint8(0, 0x02)
        view.setUint16(1, dims.cols, false)
        view.setUint16(3, dims.rows, false)
        ws.send(new Uint8Array(buf))
      }
    }

    window.addEventListener('resize', handleResize)

    return () => {
      window.removeEventListener('resize', handleResize)
      ws.close()
      term.dispose()
    }
  }, [id, token])

  return (
    <AppShell
      title={`Terminal — ${id.slice(0, 8)}`}
      subtitle="Interactive shell streamed over the control plane."
      actions={!closed && <button onClick={() => wsRef.current?.close(1000)}>Close</button>}
    >
      <div className="terminal-page">
        <Link className="page-backlink" to={`/endpoints/${id}`}>← Back to endpoint</Link>

        <div
          ref={termRef}
          role="region"
          aria-label="terminal"
          className="terminal-container"
        />

        {closed && (
          <p className="terminal-closed">
            Session closed. <Link to={`/endpoints/${id}`}>Return to endpoint</Link>
          </p>
        )}
      </div>
    </AppShell>
  )
}
