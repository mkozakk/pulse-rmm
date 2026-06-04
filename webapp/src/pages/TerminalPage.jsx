import { useEffect, useRef, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useSelector } from 'react-redux'
import { ArrowLeft, Maximize2, Minimize2, X } from 'lucide-react'
import { Terminal } from '@xterm/xterm'
import { FitAddon } from '@xterm/addon-fit'
import { useGetEndpointQuery } from '../api/pulseApi'
import AppShell from '../components/AppShell'

const WS_BASE = import.meta.env.VITE_WS_BASE ?? 'ws://localhost:8080'

export default function TerminalPage() {
  const { id } = useParams()
  const token = useSelector(state => state.auth.token)
  const termRef = useRef(null)
  const wsRef = useRef(null)
  const fsRef = useRef(null)
  const [closed, setClosed] = useState(false)
  const [isFullscreen, setIsFullscreen] = useState(false)
  const { data: ep } = useGetEndpointQuery(id)

  useEffect(() => {
    const handler = () => setIsFullscreen(!!document.fullscreenElement)
    document.addEventListener('fullscreenchange', handler)
    return () => document.removeEventListener('fullscreenchange', handler)
  }, [])

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

  function toggleFullscreen() {
    if (document.fullscreenElement) {
      document.exitFullscreen()
    } else {
      fsRef.current?.requestFullscreen()
    }
  }

  const hostname = ep?.hostname ?? id.slice(0, 8)

  return (
    <AppShell title={`Terminal - ${hostname}`}>
      <div className="stack">
        <div className="endpoint-access-bar">
          <Link to={`/endpoints/${id}`} className="icon-btn endpoint-action">
            <ArrowLeft size={14} />Endpoint
          </Link>
          <span className="remote-sep" />
          <span className={`status-dot status-dot-${ep?.status ?? 'unknown'}`} />
          <span className="endpoint-access-bar-name">{hostname}</span>
          <div style={{ flex: 1 }} />
          {closed
            ? <span className="muted" style={{ fontSize: 12 }}>Session closed</span>
            : (
              <>
                <button className="icon-btn endpoint-action" onClick={toggleFullscreen}>
                  {isFullscreen ? <Minimize2 size={14} /> : <Maximize2 size={14} />}
                  {isFullscreen ? 'Exit fullscreen' : 'Fullscreen'}
                </button>
                <button className="icon-btn endpoint-action" onClick={() => wsRef.current?.close(1000)}>
                  <X size={14} />Close session
                </button>
              </>
            )
          }
        </div>

        <div ref={fsRef} className="terminal-fs-wrap">
          <div className="terminal-fs-overlay">
            {!closed && (
              <button className="remote-fs-btn" onClick={() => wsRef.current?.close(1000)}>
                <X size={12} />Close
              </button>
            )}
            <button className="remote-fs-btn" onClick={toggleFullscreen}>
              {isFullscreen ? <Minimize2 size={12} /> : <Maximize2 size={12} />}
              {isFullscreen ? 'Exit fullscreen' : 'Fullscreen'}
            </button>
          </div>

          <div
            ref={termRef}
            role="region"
            aria-label="terminal"
            className="terminal-container"
          />
        </div>
      </div>
    </AppShell>
  )
}
