import { useState, useRef } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useSelector } from 'react-redux'
import { useListFilesQuery, useUploadFileMutation } from '../api/pulseApi'
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

  return (
    <AppShell
      title="Files"
      subtitle={path || 'Filesystem roots'}
      actions={(
        <>
          <button className="endpoint-action" disabled={!path} onClick={goUp}>Up</button>
          <button className="endpoint-action" disabled={!path || uploading} onClick={() => fileInputRef.current?.click()}>Upload here</button>
          <input ref={fileInputRef} type="file" style={{ display: 'none' }} onChange={onUploadPick} />
        </>
      )}
    >
      {status && <p className="panel-empty">{status}</p>}
      {error && <p className="error">Failed: {error?.data?.detail || error.error || 'unknown error'}</p>}
      {isFetching && <p className="panel-empty">Loading...</p>}

      <section className="panel-card">
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr>
              <th style={{ textAlign: 'left' }}>Name</th>
              <th style={{ textAlign: 'right' }}>Size</th>
              <th style={{ textAlign: 'right' }}>Modified</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {(data?.entries ?? []).map(entry => (
              <tr key={entry.path} style={{ borderTop: '1px solid #eee' }}>
                <td>
                  {entry.isDir
                    ? <button className="link-button" onClick={() => enter(entry)}>📁 {entry.name}</button>
                    : <>📄 {entry.name}</>}
                </td>
                <td style={{ textAlign: 'right' }}>{entry.isDir ? '' : fmtSize(entry.size)}</td>
                <td style={{ textAlign: 'right' }}>{entry.modified ? new Date(entry.modified).toLocaleString() : ''}</td>
                <td style={{ textAlign: 'right' }}>
                  {!entry.isDir && <button onClick={() => download(entry)}>Download</button>}
                </td>
              </tr>
            ))}
            {(data?.entries?.length ?? 0) === 0 && !isFetching && (
              <tr><td colSpan={4} className="panel-empty">Empty.</td></tr>
            )}
          </tbody>
        </table>
      </section>

      <div className="page-footer">
        <Link to={`/endpoints/${id}`}>Back to endpoint</Link>
      </div>
    </AppShell>
  )
}
