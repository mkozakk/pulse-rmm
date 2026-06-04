import { useState } from 'react'
import { Upload, Download } from 'lucide-react'

export default function FileTransferPanel({ sendFile, requestDownload }) {
  const [downloadPath, setDownloadPath] = useState('')
  const [dragOver, setDragOver] = useState(false)
  const [uploadName, setUploadName] = useState(null)

  function handleDrop(e) {
    e.preventDefault()
    setDragOver(false)
    const file = e.dataTransfer.files[0]
    if (!file) return
    setUploadName(file.name)
    sendFile(file)
  }

  function handleFileInput(e) {
    const file = e.target.files[0]
    if (!file) return
    setUploadName(file.name)
    sendFile(file)
  }

  function handleDownload() {
    if (!downloadPath.trim()) return
    requestDownload(downloadPath.trim())
  }

  return (
    <>
      <div className="panel-card stack">
        <p className="section-title">Upload to endpoint</p>
        <div
          className={`ft-drop-zone${dragOver ? ' drop-zone-active' : ''}`}
          onDragOver={e => { e.preventDefault(); setDragOver(true) }}
          onDragLeave={() => setDragOver(false)}
          onDrop={handleDrop}
        >
          <Upload size={18} style={{ color: '#94a3b8', marginBottom: '0.3rem' }} />
          {uploadName
            ? <span style={{ fontSize: 12, color: '#374151' }}>Uploading {uploadName}…</span>
            : <span style={{ fontSize: 12, color: '#94a3b8' }}>Drop a file here</span>
          }
        </div>
        <label className="ft-file-label">
          <input type="file" style={{ display: 'none' }} onChange={handleFileInput} />
          <span className="icon-btn endpoint-action" style={{ width: '100%', justifyContent: 'center', cursor: 'pointer' }}>
            Browse…
          </span>
        </label>
      </div>

      <div className="panel-card stack">
        <p className="section-title">Download from endpoint</p>
        <input
          className="ft-path-input"
          type="text"
          placeholder="Path to download (e.g. report.pdf)"
          value={downloadPath}
          onChange={e => setDownloadPath(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && handleDownload()}
        />
        <button className="icon-btn endpoint-action" style={{ width: '100%', justifyContent: 'center' }} onClick={handleDownload}>
          <Download size={13} />Download
        </button>
      </div>
    </>
  )
}
