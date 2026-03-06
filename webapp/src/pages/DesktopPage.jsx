import { useParams, Link, useNavigate } from 'react-router-dom'
import { useDesktopSession } from '../hooks/useDesktopSession'
import FileTransferPanel from '../components/FileTransferPanel'
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
    'The endpoint’s ffmpeg build has no pipewiregrab support. Reinstall ffmpeg with PipeWire enabled.',
  no_user_session:
    'No user is logged in at the endpoint. Remote desktop needs an active graphical session.',
}

function describeDesktopError(code) {
  return DESKTOP_ERROR_MESSAGES[code] || `Remote desktop failed to start (${code}).`
}

export default function DesktopPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { videoRef, status, canControl, error, sendFile, requestDownload, endSession } = useDesktopSession(id)

  async function handleEndSession() {
    await endSession()
    navigate(`/endpoints/${id}`)
  }

  return (
    <AppShell
      title={`Desktop — ${id.slice(0, 8)}`}
      subtitle="Browser-based remote view with optional control and file transfer."
      actions={(
        <>
          {!canControl && status !== 'idle' && <span className="badge badge-view-only">View Only</span>}
          {status !== 'idle' && <button onClick={handleEndSession}>End Session</button>}
        </>
      )}
    >
      <div className="desktop-page">
        <Link className="page-backlink" to={`/endpoints/${id}`}>← Back to endpoint</Link>

        {error && (
          <p className="desktop-error">{describeDesktopError(error)}</p>
        )}

        {status === 'connecting' && (
          <p className="desktop-connecting">Connecting...</p>
        )}

        <video
          ref={videoRef}
          autoPlay
          muted
          data-testid="desktop-video"
          className="desktop-video"
          style={{ display: status === 'connected' ? 'block' : 'none' }}
        />

        {status === 'connected' && (
          <FileTransferPanel sendFile={sendFile} requestDownload={requestDownload} />
        )}
      </div>
    </AppShell>
  )
}
