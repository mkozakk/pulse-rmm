import { useState } from 'react'

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

  function handleDownload() {
    if (!downloadPath.trim()) return
    requestDownload(downloadPath.trim())
  }

  return (
    <div className="file-transfer-panel">
      <div
        className={`drop-zone${dragOver ? ' drop-zone-active' : ''}`}
        onDragOver={e => { e.preventDefault(); setDragOver(true) }}
        onDragLeave={() => setDragOver(false)}
        onDrop={handleDrop}
      >
        {uploadName
          ? <span>Uploading {uploadName}...</span>
          : <span>Drop a file here to upload</span>
        }
      </div>

      <div className="download-panel">
        <input
          type="text"
          placeholder="Path to download (e.g. report.pdf)"
          value={downloadPath}
          onChange={e => setDownloadPath(e.target.value)}
        />
        <button onClick={handleDownload}>Download</button>
      </div>
    </div>
  )
}
