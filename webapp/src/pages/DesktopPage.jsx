import { useParams, Link, useNavigate } from 'react-router-dom'
import { useDesktopSession } from '../hooks/useDesktopSession'
import FileTransferPanel from '../components/FileTransferPanel'

export default function DesktopPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { videoRef, status, canControl, error, sendFile, requestDownload, endSession } = useDesktopSession(id)

  async function handleEndSession() {
    await endSession()
    navigate(`/endpoints/${id}`)
  }

  return (
    <div className="desktop-page">
      <header className="page-header desktop-header">
        <Link to={`/endpoints/${id}`}>← Back</Link>
        <h1>Desktop — {id.slice(0, 8)}</h1>
        {!canControl && status !== 'idle' && (
          <span className="badge badge-view-only">View Only</span>
        )}
        {status !== 'idle' && (
          <button onClick={handleEndSession}>End Session</button>
        )}
      </header>

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
  )
}
