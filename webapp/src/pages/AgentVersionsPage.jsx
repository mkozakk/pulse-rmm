import { useState } from 'react'
import { Upload, Star, Trash2 } from 'lucide-react'
import AppShell from '../components/AppShell'
import {
  useListAgentVersionsQuery,
  usePublishAgentVersionMutation,
  useSetCurrentAgentVersionMutation,
  useDeleteAgentVersionMutation
} from '../api/pulseApi'

export default function AgentVersionsPage() {
  const { data: versions = [] } = useListAgentVersionsQuery()
  const [publish] = usePublishAgentVersionMutation()
  const [setCurrent] = useSetCurrentAgentVersionMutation()
  const [deleteVersion] = useDeleteAgentVersionMutation()

  const [file, setFile] = useState(null)
  const [version, setVersion] = useState('')
  const [os, setOs] = useState('linux')
  const [arch, setArch] = useState('amd64')
  const [artifactType, setArtifactType] = useState('deb')
  const [uploading, setUploading] = useState(false)
  const [uploadProgress, setUploadProgress] = useState('')
  const [uploadError, setUploadError] = useState('')
  const [busy, setBusy] = useState('')

  async function handleUpload() {
    if (!file || !version) return
    setUploading(true)
    setUploadError('')
    try {
      setUploadProgress('Uploading…')
      const fd = new FormData()
      fd.append('file', file)
      fd.append('version', version)
      fd.append('os', os)
      fd.append('arch', arch)
      fd.append('artifactType', artifactType)
      await publish(fd).unwrap()

      setFile(null)
      setVersion('')
      setUploadProgress('')
    } catch (e) {
      setUploadError(e?.data?.detail ?? e?.data?.message ?? e?.message ?? 'Upload failed')
      setUploadProgress('')
    } finally {
      setUploading(false)
    }
  }

  async function handleSetCurrent(id) {
    setBusy(id + ':current')
    try {
      await setCurrent(id).unwrap()
    } finally {
      setBusy('')
    }
  }

  async function handleDelete(id) {
    if (!window.confirm('Delete this version?')) return
    setBusy(id + ':delete')
    try {
      await deleteVersion(id).unwrap()
    } catch (e) {
      alert(e?.data?.detail ?? e?.data?.message ?? 'Delete failed')
    } finally {
      setBusy('')
    }
  }

  const byVersion = versions.reduce((acc, v) => {
    if (!acc[v.version]) acc[v.version] = []
    acc[v.version].push(v)
    return acc
  }, {})

  const artifactTypeOptions = os === 'windows' ? ['zip', 'exe'] : ['deb', 'rpm', 'tar.gz']

  return (
    <AppShell title="Agent Versions" subtitle="Upload and manage agent artifacts.">
      <section className="panel-card stack">
        <h2 className="section-title">Upload artifact</h2>
        <div className="form-grid">
          <input type="file" onChange={e => setFile(e.target.files[0] ?? null)} />
          <input
            value={version}
            onChange={e => setVersion(e.target.value)}
            placeholder="Version (e.g. 1.2.3)"
          />
          <select value={os} onChange={e => { setOs(e.target.value); setArtifactType(e.target.value === 'windows' ? 'exe' : 'deb') }}>
            <option value="linux">linux</option>
            <option value="windows">windows</option>
          </select>
          <select value={arch} onChange={e => setArch(e.target.value)}>
            <option value="amd64">amd64</option>
          </select>
          <select value={artifactType} onChange={e => setArtifactType(e.target.value)}>
            {artifactTypeOptions.map(t => <option key={t} value={t}>{t}</option>)}
          </select>
          <button className="icon-btn" onClick={handleUpload} disabled={uploading || !file || !version}>
            <Upload size={14} />{uploading ? uploadProgress || 'Uploading…' : 'Upload'}
          </button>
        </div>
        {uploadError && <p className="form-error">{uploadError}</p>}
      </section>

      {Object.entries(byVersion).map(([ver, artifacts]) => (
        <section key={ver} className="panel-card stack">
          <h2 className="section-title">v{ver}</h2>
          <div className="list-card">
            {artifacts.map(v => (
              <div key={v.id} className="list-row">
                <span>
                  <span className="muted" style={{ marginRight: 8 }}>{v.os}/{v.arch}</span>
                  {v.artifactType}
                  {v.current && <span className="badge badge-green" style={{ marginLeft: 8 }}>current</span>}
                </span>
                <span className="muted" style={{ fontSize: 12 }}>
                  {(v.sizeBytes / 1024 / 1024).toFixed(1)} MB · {v.sha256.slice(0, 12)}…
                </span>
                <span style={{ display: 'flex', gap: 8 }}>
                  {!v.current && (
                    <button className="icon-btn" onClick={() => handleSetCurrent(v.id)} disabled={busy === v.id + ':current'}>
                      <Star size={13} />Set current
                    </button>
                  )}
                  <button
                    className="icon-btn"
                    onClick={() => handleDelete(v.id)}
                    disabled={busy === v.id + ':delete' || v.current}
                    title={v.current ? 'Cannot delete current version' : undefined}
                  >
                    <Trash2 size={13} />Delete
                  </button>
                </span>
              </div>
            ))}
          </div>
        </section>
      ))}

      {versions.length === 0 && (
        <p className="panel-empty">No agent artifacts uploaded yet.</p>
      )}
    </AppShell>
  )
}
