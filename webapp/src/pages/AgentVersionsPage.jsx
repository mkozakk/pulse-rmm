import { useState } from 'react'
import AppShell from '../components/AppShell'
import {
  useListAgentVersionsQuery,
  usePublishAgentVersionMutation,
  useSetCurrentAgentVersionMutation,
  useDeleteAgentVersionMutation
} from '../api/pulseApi'

export default function AgentVersionsPage() {
  const { data: versions = [], refetch } = useListAgentVersionsQuery()
  const [publish] = usePublishAgentVersionMutation()
  const [setCurrent] = useSetCurrentAgentVersionMutation()
  const [deleteVersion] = useDeleteAgentVersionMutation()

  const [file, setFile] = useState(null)
  const [version, setVersion] = useState('')
  const [os, setOs] = useState('linux')
  const [arch, setArch] = useState('amd64')
  const [uploading, setUploading] = useState(false)
  const [uploadError, setUploadError] = useState('')
  const [busy, setBusy] = useState('')

  async function handleUpload() {
    if (!file || !version) return
    setUploading(true)
    setUploadError('')
    try {
      const fd = new FormData()
      fd.append('file', file)
      fd.append('version', version)
      fd.append('os', os)
      fd.append('arch', arch)
      await publish(fd).unwrap()
      setFile(null)
      setVersion('')
      refetch()
    } catch (e) {
      setUploadError(e?.data?.message ?? 'Upload failed')
    } finally {
      setUploading(false)
    }
  }

  async function handleSetCurrent(id) {
    setBusy(id + ':current')
    try {
      await setCurrent(id).unwrap()
      refetch()
    } finally {
      setBusy('')
    }
  }

  async function handleDelete(id) {
    if (!window.confirm('Delete this version?')) return
    setBusy(id + ':delete')
    try {
      await deleteVersion(id).unwrap()
      refetch()
    } catch (e) {
      alert(e?.data?.message ?? 'Delete failed')
    } finally {
      setBusy('')
    }
  }

  const grouped = versions.reduce((acc, v) => {
    const key = `${v.os}/${v.arch}`
    if (!acc[key]) acc[key] = []
    acc[key].push(v)
    return acc
  }, {})

  return (
    <AppShell title="Agent Versions" subtitle="Upload and manage agent binaries.">
      <section className="panel-card stack">
        <h2 className="section-title">Upload new version</h2>
        <div className="form-grid">
          <input
            type="file"
            onChange={e => setFile(e.target.files[0] ?? null)}
          />
          <input
            value={version}
            onChange={e => setVersion(e.target.value)}
            placeholder="Version (e.g. 1.2.3)"
          />
          <select value={os} onChange={e => setOs(e.target.value)}>
            <option value="linux">linux</option>
            <option value="windows">windows</option>
          </select>
          <select value={arch} onChange={e => setArch(e.target.value)}>
            <option value="amd64">amd64</option>
            <option value="arm64">arm64</option>
          </select>
          <button onClick={handleUpload} disabled={uploading || !file || !version}>
            {uploading ? 'Uploading…' : 'Upload'}
          </button>
        </div>
        {uploadError && <p className="form-error">{uploadError}</p>}
      </section>

      {Object.entries(grouped).map(([platform, rows]) => (
        <section key={platform} className="panel-card stack">
          <h2 className="section-title">{platform}</h2>
          <div className="list-card">
            {rows.map(v => (
              <div key={v.id} className="list-row">
                <span>
                  {v.version}
                  {v.current && <span className="badge badge-green" style={{ marginLeft: 8 }}>current</span>}
                </span>
                <span className="muted">{v.sizeBytes.toLocaleString()} bytes · {v.sha256.slice(0, 12)}…</span>
                <span style={{ display: 'flex', gap: 8 }}>
                  {!v.current && (
                    <button
                      onClick={() => handleSetCurrent(v.id)}
                      disabled={busy === v.id + ':current'}
                    >
                      Set current
                    </button>
                  )}
                  <button
                    onClick={() => handleDelete(v.id)}
                    disabled={busy === v.id + ':delete' || v.current}
                    title={v.current ? 'Cannot delete current version' : undefined}
                  >
                    Delete
                  </button>
                </span>
              </div>
            ))}
          </div>
        </section>
      ))}

      {versions.length === 0 && (
        <p className="panel-empty">No agent versions uploaded yet.</p>
      )}
    </AppShell>
  )
}
