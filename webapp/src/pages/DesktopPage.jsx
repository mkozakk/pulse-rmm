import { useParams, Link, useNavigate } from 'react-router-dom'
import { useDesktopSession } from '../hooks/useDesktopSession'
import FileTransferPanel from '../components/FileTransferPanel'
import AppShell from '../components/AppShell'

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

        {error === 'wayland_not_supported' && (
          <p className="desktop-error">
            Remote desktop is not available. The endpoint user must accept the screen share prompt.
          </p>
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
