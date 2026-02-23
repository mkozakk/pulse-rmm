import { useEffect, useRef, useState } from 'react'
import { useSelector } from 'react-redux'
import { useCreateSessionMutation, useEndSessionMutation } from '../api/pulseApi'

const WS_BASE = import.meta.env.VITE_WS_BASE ?? 'ws://localhost:8080'

export function useDesktopSession(endpointId) {
  const token = useSelector(state => state.auth.token)
  const [createSession] = useCreateSessionMutation()
  const [deleteSession] = useEndSessionMutation()

  const videoRef = useRef(null)
  const pcRef = useRef(null)
  const wsRef = useRef(null)
  const inputChannelRef = useRef(null)
  const fileChannelRef = useRef(null)
  const sessionIdRef = useRef(null)

  const [status, setStatus] = useState('idle')
  const [canControl, setCanControl] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    if (!endpointId || !token) return

    let cancelled = false
    setStatus('connecting')

    async function start() {
      const session = await createSession({ endpointId, type: 'desktop' }).unwrap()
      if (cancelled) {
        deleteSession(session.sessionId)
        return
      }

      const sid = session.sessionId
      sessionIdRef.current = sid
      setCanControl(!!session.canControl)

      const iceServers = [
        { urls: 'stun:stun.l.google.com:19302' },
        ...(session.turnUrls?.length ? [{
          urls: session.turnUrls,
          username: session.turnUsername,
          credential: session.turnCredential
        }] : [])
      ]

      const pc = new RTCPeerConnection({ iceServers })
      pcRef.current = pc

      pc.ontrack = (e) => {
        if (videoRef.current) videoRef.current.srcObject = e.streams[0]
        setStatus('connected')
      }

      const ws = new WebSocket(`${WS_BASE}/ws/sessions/${sid}/signal?token=${token}`)
      wsRef.current = ws

      ws.onclose = () => { if (!cancelled) setStatus(s => s === 'connecting' ? 'error' : s) }

      await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject })
      if (cancelled) return

      pc.onicecandidate = (e) => {
        if (e.candidate && ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ type: 'candidate', payload: JSON.stringify(e.candidate) }))
        }
      }

      if (session.canControl) {
        inputChannelRef.current = pc.createDataChannel('input')
      }
      fileChannelRef.current = pc.createDataChannel('file-transfer')

      ws.onmessage = async (e) => {
        const msg = JSON.parse(e.data)
        if (msg.type === 'session_ready') {
          pc.addTransceiver('video', { direction: 'recvonly' })
          const offer = await pc.createOffer()
          await pc.setLocalDescription(offer)
          ws.send(JSON.stringify({ type: 'offer', payload: offer.sdp }))
        } else if (msg.type === 'answer') {
          await pc.setRemoteDescription({ type: 'answer', sdp: msg.payload })
        } else if (msg.type === 'candidate') {
          await pc.addIceCandidate(JSON.parse(msg.payload))
        } else if (msg.type === 'error') {
          if (msg.code === 'wayland_not_supported') {
            setError('wayland_not_supported')
          }
          setStatus('error')
        }
      }
    }

    start().catch(() => { if (!cancelled) setStatus('error') })

    return () => {
      cancelled = true
      const sid = sessionIdRef.current
      if (sid) {
        deleteSession(sid).catch(() => {})
      }
      wsRef.current?.close()
      pcRef.current?.close()
    }
  }, [endpointId, token])

  useEffect(() => {
    if (!canControl || status !== 'connected') return
    const video = videoRef.current
    if (!video) return

    function send(evt) {
      const ch = inputChannelRef.current
      if (ch?.readyState === 'open') ch.send(JSON.stringify(evt))
    }

    function onMouseMove(e) {
      const r = video.getBoundingClientRect()
      const sx = video.videoWidth / r.width || 1
      const sy = video.videoHeight / r.height || 1
      send({ type: 'mousemove', x: Math.round((e.clientX - r.left) * sx), y: Math.round((e.clientY - r.top) * sy) })
    }
    function onMouseDown(e) { send({ type: 'mousedown', button: e.button }) }
    function onMouseUp(e) { send({ type: 'mouseup', button: e.button }) }
    function onKeyDown(e) {
      e.preventDefault()
      send({ type: 'keydown', keyCode: e.keyCode })
    }
    function onKeyUp(e) {
      e.preventDefault()
      send({ type: 'keyup', keyCode: e.keyCode })
    }
    function onWheel(e) {
      e.preventDefault()
      send({ type: 'wheel', deltaX: Math.round(e.deltaX), deltaY: Math.round(e.deltaY) })
    }

    video.tabIndex = 0
    video.addEventListener('mousemove', onMouseMove)
    video.addEventListener('mousedown', onMouseDown)
    video.addEventListener('mouseup', onMouseUp)
    video.addEventListener('keydown', onKeyDown)
    video.addEventListener('keyup', onKeyUp)
    video.addEventListener('wheel', onWheel, { passive: false })

    return () => {
      video.removeEventListener('mousemove', onMouseMove)
      video.removeEventListener('mousedown', onMouseDown)
      video.removeEventListener('mouseup', onMouseUp)
      video.removeEventListener('keydown', onKeyDown)
      video.removeEventListener('keyup', onKeyUp)
      video.removeEventListener('wheel', onWheel)
    }
  }, [canControl, status])

  function sendFile(file) {
    const ch = fileChannelRef.current
    if (!ch || ch.readyState !== 'open') return
    const CHUNK = 64 * 1024
    let offset = 0
    const reader = new FileReader()
    ch.send(JSON.stringify({ type: 'upload_start', name: file.name, size: file.size }))
    reader.onload = (e) => {
      ch.send(e.target.result)
      offset += e.target.result.byteLength
      if (offset < file.size) {
        reader.readAsArrayBuffer(file.slice(offset, offset + CHUNK))
      } else {
        ch.send(JSON.stringify({ type: 'upload_done' }))
      }
    }
    reader.readAsArrayBuffer(file.slice(0, CHUNK))
  }

  function requestDownload(path) {
    const ch = fileChannelRef.current
    if (ch?.readyState === 'open') ch.send(JSON.stringify({ type: 'download_request', path }))
  }

  async function endSession() {
    const sid = sessionIdRef.current
    if (sid) {
      try { await deleteSession(sid).unwrap() } catch {}
      sessionIdRef.current = null
    }
    wsRef.current?.close()
    pcRef.current?.close()
    setStatus('idle')
  }

  return { videoRef, status, canControl, error, sendFile, requestDownload, endSession }
}
