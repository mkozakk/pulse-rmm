import { useEffect, useMemo, useRef, useState } from 'react'
import { Upload, Star, Trash2, FileCheck } from 'lucide-react'
import AppShell from '../components/AppShell'
import {
  useListAgentVersionsQuery,
  usePublishAgentVersionMutation,
  useSetCurrentAgentVersionMutation,
  useDeleteAgentVersionMutation
} from '../api/pulseApi'

function inferNextVersion(versions) {
  const parsed = versions
    .map(v => v.version)
    .filter(Boolean)
    .map(v => v.split('.').map(Number))
    .filter(parts => parts.length > 0 && parts.every(n => Number.isFinite(n)))
    .sort((a, b) => {
      const len = Math.max(a.length, b.length)
      for (let i = 0; i < len; i++) {
        const diff = (b[i] ?? 0) - (a[i] ?? 0)
        if (diff !== 0) return diff
      }
      return 0
    })
  if (!parsed.length) return ''
  const latest = [...parsed[0]]
  latest[latest.length - 1] += 1
  return latest.join('.')
}

function parseFilename(name) {
  const result = {}
  const vm = name.match(/(\d+\.\d+\.\d+)/)
  if (vm) result.version = vm[1]
  if (/amd64|x86_64/i.test(name)) result.arch = 'amd64'
  // extension is the primary OS/type signal
  if (name.endsWith('.tar.gz')) { result.artifactType = 'tar.gz'; result.os = 'linux' }
  else if (name.endsWith('.deb')) { result.artifactType = 'deb'; result.os = 'linux' }
  else if (name.endsWith('.rpm')) { result.artifactType = 'rpm'; result.os = 'linux' }
  else if (name.endsWith('.exe')) { result.artifactType = 'exe'; result.os = 'windows' }
  else if (name.endsWith('.zip')) { result.artifactType = 'zip'; result.os = 'windows' }
  // fall back to filename text if extension didn't tell us the OS
  if (!result.os) {
    if (/win/i.test(name)) result.os = 'windows'
    else if (/linux/i.test(name)) result.os = 'linux'
  }
  return result
}

export default function AgentVersionsPage() {
  const { data: versions = [] } = useListAgentVersionsQuery()
  const [publish] = usePublishAgentVersionMutation()
  const [setCurrent] = useSetCurrentAgentVersionMutation()
  const [deleteVersion] = useDeleteAgentVersionMutation()

  const fileInputRef = useRef(null)
  const [file, setFile] = useState(null)
  const [dragOver, setDragOver] = useState(false)
  const [version, setVersion] = useState('')
  const [os, setOs] = useState('linux')
  const [arch, setArch] = useState('amd64')
  const [artifactType, setArtifactType] = useState('deb')
  const [uploading, setUploading] = useState(false)
  const [uploadProgress, setUploadProgress] = useState('')
  const [uploadError, setUploadError] = useState('')
  const [busy, setBusy] = useState('')

  const suggestedVersion = useMemo(() => inferNextVersion(versions), [versions])

  useEffect(() => {
    if (!version && suggestedVersion) setVersion(suggestedVersion)
  }, [suggestedVersion])

  function handleFileSelect(f) {
    if (!f) return
    setFile(f)
    const parsed = parseFilename(f.name)
    if (parsed.version) setVersion(parsed.version)
    if (parsed.os) {
      setOs(parsed.os)
      setArtifactType(parsed.os === 'windows' ? 'exe' : 'deb')
    }
    if (parsed.artifactType) setArtifactType(parsed.artifactType)
    if (parsed.arch) setArch(parsed.arch)
  }

  function handleOsChange(newOs) {
    setOs(newOs)
    setArtifactType(newOs === 'windows' ? 'exe' : 'deb')
  }

  function handleArtifactTypeChange(type) {
    setArtifactType(type)
    if (type === 'exe' || type === 'zip') setOs('windows')
    else if (type === 'deb' || type === 'rpm' || type === 'tar.gz') setOs('linux')
  }

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
    <AppShell title="Agent Versions">
      <div className="stack">
        <section className="panel-card stack">
          <h2 className="section-title">Upload artifact</h2>

          <div
            className={`agent-drop-zone${dragOver ? ' drop-active' : ''}${file ? ' has-file' : ''}`}
            onClick={() => fileInputRef.current.click()}
            onDragOver={e => { e.preventDefault(); setDragOver(true) }}
            onDragLeave={() => setDragOver(false)}
            onDrop={e => { e.preventDefault(); setDragOver(false); handleFileSelect(e.dataTransfer.files[0]) }}
          >
            <input
              ref={fileInputRef}
              type="file"
              style={{ display: 'none' }}
              onChange={e => handleFileSelect(e.target.files[0] ?? null)}
            />
            {file ? (
              <>
                <FileCheck size={26} className="agent-drop-zone-icon" />
                <span className="agent-drop-zone-label">{file.name}</span>
                <span className="agent-drop-zone-hint">{(file.size / 1024 / 1024).toFixed(1)} MB · click to replace</span>
              </>
            ) : (
              <>
                <Upload size={26} className="agent-drop-zone-icon" />
                <span className="agent-drop-zone-label">Drop artifact here or click to browse</span>
                <span className="agent-drop-zone-hint">.deb · .rpm · .tar.gz · .exe · .zip</span>
              </>
            )}
          </div>

          <div className="form-row">
            <div className="form-field">
              <label className="field-label">Version</label>
              <input
                value={version}
                onChange={e => setVersion(e.target.value)}
                placeholder="Version"
              />
            </div>
            <div className="form-field">
              <label className="field-label">OS</label>
              <select value={os} onChange={e => handleOsChange(e.target.value)}>
                <option value="linux">Linux</option>
                <option value="windows">Windows</option>
              </select>
            </div>
            <div className="form-field">
              <label className="field-label">Arch</label>
              <select value={arch} onChange={e => setArch(e.target.value)}>
                <option value="amd64">amd64</option>
              </select>
            </div>
            <div className="form-field">
              <label className="field-label">Artifact type</label>
              <select value={artifactType} onChange={e => handleArtifactTypeChange(e.target.value)}>
                {artifactTypeOptions.map(t => <option key={t} value={t}>{t}</option>)}
              </select>
            </div>
          </div>

          <div className="form-actions">
            <button
              className="icon-btn btn-primary endpoint-action"
              onClick={handleUpload}
              disabled={uploading || !file || !version}
            >
              <Upload size={14} />{uploading ? uploadProgress || 'Uploading…' : 'Upload'}
            </button>
          </div>

          {uploadError && <p className="form-error">{uploadError}</p>}
        </section>

        {Object.entries(byVersion).map(([ver, artifacts]) => (
          <section key={ver} className="panel-card stack">
            <h2 className="section-title">v{ver}</h2>
            <table className="enrolment-table">
              <thead>
                <tr>
                  <th>Platform</th>
                  <th>Type</th>
                  <th></th>
                  <th>Size / SHA</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {artifacts.map(v => (
                  <tr key={v.id}>
                    <td className="col-muted">{v.os}/{v.arch}</td>
                    <td>{v.artifactType}</td>
                    <td>{v.current && <span className="badge badge-green">current</span>}</td>
                    <td className="col-muted" style={{ fontSize: 12 }}>
                      {(v.sizeBytes / 1024 / 1024).toFixed(1)} MB · {v.sha256.slice(0, 12)}…
                    </td>
                    <td className="col-right">
                      <span style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
                        {!v.current && (
                          <button className="icon-btn endpoint-action" onClick={() => handleSetCurrent(v.id)} disabled={busy === v.id + ':current'}>
                            <Star size={12} />Set current
                          </button>
                        )}
                        <button
                          className="icon-btn endpoint-action proc-kill-btn"
                          onClick={() => handleDelete(v.id)}
                          disabled={busy === v.id + ':delete' || v.current}
                          title={v.current ? 'Cannot delete current version' : undefined}
                        >
                          <Trash2 size={12} />Delete
                        </button>
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
        ))}

        {versions.length === 0 && (
          <p className="panel-empty">No agent artifacts uploaded yet.</p>
        )}
      </div>
    </AppShell>
  )
}
