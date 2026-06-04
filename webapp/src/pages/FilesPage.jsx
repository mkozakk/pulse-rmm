import { useState, useRef } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useSelector } from 'react-redux'
import { ArrowLeft, Upload, Folder, File, Download } from 'lucide-react'
import { useListFilesQuery, useUploadFileMutation, useGetEndpointQuery } from '../api/pulseApi'
import AppShell from '../components/AppShell'

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080/api'

function fmtSize(n) {
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} MB`
  return `${(n / 1024 / 1024 / 1024).toFixed(2)} GB`
}

export default function FilesPage() {
  const { id } = useParams()
  const [path, setPath] = useState('')
  const token = useSelector(s => s.auth.token)
  const { data: ep } = useGetEndpointQuery(id)
  const { data, isFetching, error, refetch } = useListFilesQuery({ id, path })
  const [uploadFile, { isLoading: uploading }] = useUploadFileMutation()
  const fileInputRef = useRef(null)
  const [status, setStatus] = useState('')

  const enter = (entry) => {
    if (entry.isDir) setPath(entry.path)
  }

  const goUp = () => {
    if (!path) return
    const sep = path.includes('\\') ? '\\' : '/'
    const parts = path.split(sep).filter(Boolean)
    if (parts.length <= 1) { setPath(''); return }
    parts.pop()
    const next = path.startsWith('/') ? '/' + parts.join('/') : parts.join(sep)
    setPath(next)
  }

  const download = async (entry) => {
    setStatus(`Downloading ${entry.name}...`)
    try {
      const res = await fetch(
        `${API_BASE}/files/${id}/download?path=${encodeURIComponent(entry.path)}`,
        { headers: { Authorization: `Bearer ${token}` }, credentials: 'include' }
      )
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const blob = await res.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = entry.name
      a.click()
      URL.revokeObjectURL(url)
      setStatus(`Downloaded ${entry.name}`)
    } catch (e) {
      setStatus(`Download failed: ${e.message}`)
    }
  }

  const onUploadPick = async (e) => {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file || !path) return
    const sep = path.includes('\\') ? '\\' : '/'
    const dest = path.endsWith(sep) ? path + file.name : path + sep + file.name
    setStatus(`Uploading ${file.name} to ${dest}...`)
    try {
      const res = await uploadFile({ id, path: dest, file }).unwrap()
      setStatus(`Uploaded ${res.bytes} bytes`)
      refetch()
    } catch (err) {
      setStatus(`Upload failed: ${err?.data?.detail || err.error || 'unknown'}`)
    }
  }

  const hostname = ep?.hostname ?? id.slice(0, 8)

  return (
    <AppShell title={`Files - ${hostname}`}>
      <div className="stack">
        <div className="endpoint-access-bar">
          <Link to={`/endpoints/${id}`} className="icon-btn endpoint-action">
            <ArrowLeft size={14} />Endpoint
          </Link>
          <span className="remote-sep" />
          <span className={`status-dot status-dot-${ep?.status ?? 'unknown'}`} />
          <span className="endpoint-access-bar-name">{hostname}</span>
          <span className="files-breadcrumb">{path || 'Filesystem roots'}</span>
          <div style={{ flex: 1 }} />
          <button
            className="icon-btn endpoint-action"
            disabled={!path || uploading}
            onClick={() => fileInputRef.current?.click()}
          >
            <Upload size={14} />{uploading ? 'Uploading…' : 'Upload here'}
          </button>
          <input ref={fileInputRef} type="file" style={{ display: 'none' }} onChange={onUploadPick} />
        </div>

        {status && <p className="panel-empty">{status}</p>}
        {error && <p className="error">Failed: {error?.data?.detail || error.error || 'unknown error'}</p>}

        <div className="panel-card">
          <table className="files-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Size</th>
                <th>Modified</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {isFetching && (
                <tr><td colSpan={4} className="panel-empty">Loading…</td></tr>
              )}
              {!isFetching && path && (
                <tr>
                  <td colSpan={4}>
                    <button className="file-entry-btn" onClick={goUp}>
                      <Folder size={13} className="file-icon file-icon-dir" />
                      ..
                    </button>
                  </td>
                </tr>
              )}
              {!isFetching && (data?.entries ?? []).map(entry => (
                <tr key={entry.path}>
                  <td>
                    {entry.isDir
                      ? (
                        <button className="file-entry-btn" onClick={() => enter(entry)}>
                          <Folder size={13} className="file-icon file-icon-dir" />
                          {entry.name}
                        </button>
                      )
                      : (
                        <span className="file-entry-name">
                          <File size={13} className="file-icon" />
                          {entry.name}
                        </span>
                      )
                    }
                  </td>
                  <td className="col-right">{entry.isDir ? '' : fmtSize(entry.size)}</td>
                  <td className="col-right col-muted">{entry.modified ? new Date(entry.modified).toLocaleString() : ''}</td>
                  <td className="col-right">
                    {!entry.isDir && (
                      <button className="icon-btn endpoint-action" onClick={() => download(entry)}>
                        <Download size={12} />Download
                      </button>
                    )}
                  </td>
                </tr>
              ))}
              {!isFetching && (data?.entries?.length ?? 0) === 0 && (
                <tr><td colSpan={4} className="panel-empty">Empty.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </AppShell>
  )
}
