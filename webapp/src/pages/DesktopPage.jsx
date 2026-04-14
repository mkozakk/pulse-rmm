import { useEffect, useRef, useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { ArrowLeft, Volume2, VolumeX, MonitorOff, AlertCircle, Maximize2, Minimize2 } from 'lucide-react'
import { useDesktopSession } from '../hooks/useDesktopSession'
import { useGetEndpointQuery } from '../api/pulseApi'
import AppShell from '../components/AppShell'

const DESKTOP_ERROR_MESSAGES = {
  wayland_not_supported:
    'Remote desktop is not available on this endpoint (Wayland session without portal support).',
  wayland_portal_missing:
    'The endpoint is missing xdg-desktop-portal. Install it (e.g. `dnf install xdg-desktop-portal-gnome` or the KDE equivalent) and retry.',
  wayland_consent_denied:
    'The user at the endpoint declined the screen-share prompt. Ask them to accept it and start a new session.',
  wayland_consent_timeout:
    'The user at the endpoint did not respond to the screen-share prompt in time.',
  wayland_no_stream:
    'The screen-share portal returned no stream. Ask the user to try again and pick a monitor.',
  wayland_ffmpeg_no_pipewire:
    "The endpoint's ffmpeg build has no pipewiregrab support. Reinstall ffmpeg with PipeWire enabled.",
  no_user_session:
    'No user is logged in at the endpoint. Remote desktop needs an active graphical session.',
}

function describeDesktopError(code) {
  return DESKTOP_ERROR_MESSAGES[code] || `Remote desktop failed to start (${code}).`
}

function statusBadge(status) {
  if (status === 'connecting') return <span className="badge badge-blue">Connecting…</span>
  if (status === 'connected') return <span className="badge badge-green">Connected</span>
  if (status === 'error') return <span className="badge badge-red">Error</span>
  return null
}

export default function DesktopPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { videoRef, status, canControl, error, endSession } = useDesktopSession(id)
  const [audioOn, setAudioOn] = useState(false)
  const [isFullscreen, setIsFullscreen] = useState(false)
  const fsRef = useRef(null)
  const { data: ep } = useGetEndpointQuery(id)

  useEffect(() => {
    const handler = () => setIsFullscreen(!!document.fullscreenElement)
    document.addEventListener('fullscreenchange', handler)
    return () => document.removeEventListener('fullscreenchange', handler)
  }, [])

  async function handleEndSession() {
    await endSession()
    navigate(`/endpoints/${id}`)
  }

  function toggleAudio() {
    const v = videoRef.current
    if (!v) return
    v.muted = audioOn
    setAudioOn(!audioOn)
  }

  function toggleFullscreen() {
    if (document.fullscreenElement) {
      document.exitFullscreen()
    } else {
      fsRef.current?.requestFullscreen()
    }
  }

  const isConnected = status === 'connected'
  const hostname = ep?.hostname ?? id.slice(0, 8)

  return (
    <AppShell title={`Desktop - ${hostname}`}>
      <div className="stack">
        <div className="endpoint-access-bar">
          <Link to={`/endpoints/${id}`} className="icon-btn endpoint-action">
            <ArrowLeft size={14} />Endpoint
          </Link>
          <span className="remote-sep" />
          <span className={`status-dot status-dot-${ep?.status ?? 'unknown'}`} />
          <span className="endpoint-access-bar-name">{hostname}</span>
          {!canControl && status !== 'idle' && (
            <span className="badge badge-view-only">View Only</span>
          )}
          {statusBadge(status)}
          <div style={{ flex: 1 }} />
          {isConnected && (
            <button className="icon-btn endpoint-action" onClick={toggleAudio}>
              {audioOn ? <VolumeX size={14} /> : <Volume2 size={14} />}
              {audioOn ? 'Mute' : 'Unmute'}
            </button>
          )}
          {isConnected && (
            <button className="icon-btn endpoint-action" onClick={toggleFullscreen}>
              {isFullscreen ? <Minimize2 size={14} /> : <Maximize2 size={14} />}
              {isFullscreen ? 'Exit fullscreen' : 'Fullscreen'}
            </button>
          )}
          {status !== 'idle' && (
            <button className="icon-btn endpoint-action btn-end-session" onClick={handleEndSession}>
              <MonitorOff size={14} />End Session
            </button>
          )}
        </div>

        {error && (
          <div className="desktop-error-banner">
            <AlertCircle size={16} style={{ flexShrink: 0, marginTop: 1 }} />
            <span>{describeDesktopError(error)}</span>
          </div>
        )}

        <div ref={fsRef} className="desktop-fs-wrap">
          <div className="desktop-fs-overlay">
            {isConnected && (
              <button className="remote-fs-btn" onClick={toggleAudio}>
                {audioOn ? <VolumeX size={12} /> : <Volume2 size={12} />}
                {audioOn ? 'Mute' : 'Unmute'}
              </button>
            )}
            {status !== 'idle' && (
              <button className="remote-fs-btn btn-end-session" onClick={handleEndSession}>
                <MonitorOff size={12} />End Session
              </button>
            )}
            <button className="remote-fs-btn" onClick={toggleFullscreen}>
              {isFullscreen ? <Minimize2 size={12} /> : <Maximize2 size={12} />}
              {isFullscreen ? 'Exit fullscreen' : 'Fullscreen'}
            </button>
          </div>

          <video
            ref={videoRef}
            autoPlay
            muted
            data-testid="desktop-video"
            className="desktop-video"
            style={{ display: isConnected ? 'block' : 'none' }}
          />
        </div>
      </div>
    </AppShell>
  )
}
