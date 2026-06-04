import { useMemo, useState } from 'react'
import { Key, FolderPlus, Tag, RefreshCw, Copy, Check } from 'lucide-react'
import AppShell from '../components/AppShell'
import {
  useCreateEnrolmentTokenMutation,
  useCreateGroupMutation,
  useCreateTagRuleMutation,
  useEvaluateTagRulesMutation,
  useGetEndpointsQuery,
  useGetGroupsQuery,
  useGetTagRulesQuery,
  useUpdateEndpointGroupMutation,
  useUpdateEndpointTagsMutation
} from '../api/pulseApi'

function CopyLine({ label, text }) {
  const [copied, setCopied] = useState(false)
  function copy() {
    navigator.clipboard.writeText(text)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }
  return (
    <div>
      {label && <p style={{ marginBottom: 4, fontSize: 12, color: '#6b7280' }}>{label}</p>}
      <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
        <code style={{ flex: 1, padding: '6px 8px', background: '#1a1a2e', borderRadius: 8, fontSize: 12, wordBreak: 'break-all', color: '#e2e8f0' }}>
          {text}
        </code>
        <button className="icon-btn endpoint-action" onClick={copy} style={{ whiteSpace: 'nowrap' }}>
          {copied ? <><Check size={13} />Copied!</> : <><Copy size={13} />Copy</>}
        </button>
      </div>
    </div>
  )
}

function InstallCommands({ token }) {
  return (
    <div className="stack" style={{ marginTop: 8 }}>
      <p className="panel-empty">Token {token.id} - expires {token.expiresAt}</p>
      <CopyLine label="Linux (Debian/Ubuntu/Fedora/RHEL) - run as root:" text={token.installSh} />
      <CopyLine label="Windows - PowerShell as Administrator:" text={token.installPs1} />
    </div>
  )
}

function TagsEditor({ endpoint, groups, onSave, saving }) {
  const [groupId, setGroupId] = useState(endpoint.groupId ?? '')
  const [tagsText, setTagsText] = useState((endpoint.tags ?? []).map(t => `${t.key}=${t.value}`).join(', '))

  async function handleSave() {
    const tags = tagsText.split(',').map(t => t.trim()).filter(Boolean).map(t => {
      const [key, ...rest] = t.split('=')
      return { key: key.trim(), value: rest.join('=').trim() }
    })
    await onSave({ groupId, tags })
  }

  return (
    <div className="stack">
      <div className="form-row">
        <div className="form-field">
          <label className="field-label">Group</label>
          <select value={groupId} onChange={e => setGroupId(e.target.value)}>
            <option value="">No group</option>
            {groups.map(g => <option key={g.id} value={g.id}>{g.name}</option>)}
          </select>
        </div>
        <div className="form-field">
          <label className="field-label">Tags</label>
          <input value={tagsText} onChange={e => setTagsText(e.target.value)} placeholder="env=prod, site=warsaw" />
        </div>
      </div>
      <div className="form-actions">
        <button className="icon-btn btn-primary" onClick={handleSave} disabled={saving}>Save</button>
      </div>
    </div>
  )
}

export default function EnrolmentPage() {
  const endpoints = useGetEndpointsQuery(undefined, { pollingInterval: 30000 })
  const groups = useGetGroupsQuery(undefined, { pollingInterval: 30000 })
  const tagRules = useGetTagRulesQuery(undefined, { pollingInterval: 30000 })
  const [createToken] = useCreateEnrolmentTokenMutation()
  const [createGroup] = useCreateGroupMutation()
  const [createRule] = useCreateTagRuleMutation()
  const [evaluateRules] = useEvaluateTagRulesMutation()
  const [updateGroup] = useUpdateEndpointGroupMutation()
  const [updateTags] = useUpdateEndpointTagsMutation()
  const [tokenGroupId, setTokenGroupId] = useState('')
  const [tokenTtl, setTokenTtl] = useState('24')
  const [groupName, setGroupName] = useState('')
  const [parentId, setParentId] = useState('')
  const [rule, setRule] = useState({ conditionField: 'hostname', conditionValue: '', tagKey: '', tagValue: '' })
  const [tokenResult, setTokenResult] = useState(null)
  const [busy, setBusy] = useState('')

  const groupOptions = useMemo(() => groups.data ?? [], [groups.data])
  const endpointRows = endpoints.data ?? []

  async function handleCreateToken() {
    setBusy('token')
    try {
      const result = await createToken({ groupId: tokenGroupId, ttlHours: Number(tokenTtl) || 24 }).unwrap()
      setTokenResult(result)
    } finally { setBusy('') }
  }

  async function handleCreateGroup() {
    setBusy('group')
    try {
      await createGroup({ name: groupName, parentId: parentId || null }).unwrap()
      setGroupName('')
      setParentId('')
    } finally { setBusy('') }
  }

  async function handleCreateRule() {
    setBusy('rule')
    try {
      await createRule(rule).unwrap()
      setRule({ conditionField: 'hostname', conditionValue: '', tagKey: '', tagValue: '' })
    } finally { setBusy('') }
  }

  async function handleUpdateEndpoint(endpointId, payload) {
    setBusy(endpointId)
    try {
      if (payload.groupId !== undefined) await updateGroup({ id: endpointId, groupId: payload.groupId || null }).unwrap()
      if (payload.tags !== undefined) await updateTags({ id: endpointId, tags: payload.tags }).unwrap()
    } finally { setBusy('') }
  }

  return (
    <AppShell title="Enrolment">
      <div className="stack">
        <section className="panel-card stack">
          <h2 className="section-title">Create enrolment token</h2>
          <div className="form-row">
            <div className="form-field">
              <label className="field-label">Group</label>
              <select value={tokenGroupId} onChange={e => setTokenGroupId(e.target.value)}>
                <option value="">Select group</option>
                {groupOptions.map(g => <option key={g.id} value={g.id}>{g.name}</option>)}
              </select>
            </div>
            <div className="form-field" style={{ flex: '0 0 130px' }}>
              <label className="field-label">TTL (hours)</label>
              <input value={tokenTtl} onChange={e => setTokenTtl(e.target.value)} type="number" min="1" />
            </div>
          </div>
          <div className="form-actions">
            <button className="icon-btn btn-primary" onClick={handleCreateToken} disabled={busy === 'token'}>
              <Key size={14} />Create token
            </button>
          </div>
          {tokenResult && <InstallCommands token={tokenResult} />}
        </section>

        <section className="panel-card stack">
          <h2 className="section-title">Groups</h2>
          <div className="form-row">
            <div className="form-field">
              <label className="field-label">Name</label>
              <input value={groupName} onChange={e => setGroupName(e.target.value)} placeholder="e.g. Servers" />
            </div>
            <div className="form-field">
              <label className="field-label">Parent group</label>
              <select value={parentId} onChange={e => setParentId(e.target.value)}>
                <option value="">No parent</option>
                {groupOptions.map(g => <option key={g.id} value={g.id}>{g.name}</option>)}
              </select>
            </div>
          </div>
          <div className="form-actions">
            <button className="icon-btn btn-primary" onClick={handleCreateGroup} disabled={busy === 'group'}>
              <FolderPlus size={14} />Create group
            </button>
          </div>
          {groupOptions.length === 0
            ? <p className="panel-empty">No groups yet.</p>
            : (
              <table className="enrolment-table">
                <thead><tr><th>Name</th><th>Parent</th></tr></thead>
                <tbody>
                  {groupOptions.map(g => (
                    <tr key={g.id}>
                      <td style={{ fontWeight: 500 }}>{g.name}</td>
                      <td className="col-muted">
                        {g.parentId ? groupOptions.find(p => p.id === g.parentId)?.name ?? g.parentId : 'root'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )
          }
        </section>

        <section className="panel-card stack">
          <h2 className="section-title">Tag rules</h2>
          <div className="form-row">
            <div className="form-field">
              <label className="field-label">Condition field</label>
              <input value={rule.conditionField} onChange={e => setRule({ ...rule, conditionField: e.target.value })} placeholder="hostname" />
            </div>
            <div className="form-field">
              <label className="field-label">Condition value</label>
              <input value={rule.conditionValue} onChange={e => setRule({ ...rule, conditionValue: e.target.value })} placeholder="web-*" />
            </div>
            <div className="form-field">
              <label className="field-label">Tag key</label>
              <input value={rule.tagKey} onChange={e => setRule({ ...rule, tagKey: e.target.value })} placeholder="role" />
            </div>
            <div className="form-field">
              <label className="field-label">Tag value</label>
              <input value={rule.tagValue} onChange={e => setRule({ ...rule, tagValue: e.target.value })} placeholder="web" />
            </div>
          </div>
          <div className="form-actions">
            <button className="icon-btn btn-primary" onClick={handleCreateRule} disabled={busy === 'rule'}>
              <Tag size={14} />Create rule
            </button>
            <button className="icon-btn endpoint-action" onClick={() => evaluateRules().unwrap()} disabled={busy === 'rule'}>
              <RefreshCw size={14} />Evaluate all
            </button>
          </div>
          {(tagRules.data ?? []).length === 0
            ? <p className="panel-empty">No tag rules yet.</p>
            : (
              <table className="enrolment-table">
                <thead><tr><th>Condition</th><th>Tag</th></tr></thead>
                <tbody>
                  {(tagRules.data ?? []).map(item => (
                    <tr key={item.id}>
                      <td style={{ fontFamily: 'monospace', fontSize: 12 }}>{item.conditionField} = {item.conditionValue}</td>
                      <td className="col-muted" style={{ fontFamily: 'monospace', fontSize: 12 }}>{item.tagKey}={item.tagValue}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )
          }
        </section>

        <section className="panel-card stack">
          <h2 className="section-title">Endpoints</h2>
          {endpointRows.length === 0
            ? <p className="panel-empty">No endpoints enrolled yet.</p>
            : endpointRows.map(ep => (
              <div key={ep.id} className="enrolment-endpoint-block">
                <div className="enrolment-endpoint-row">
                  <span className={`status-dot status-dot-${ep.status ?? 'unknown'}`} />
                  <strong style={{ fontSize: 13 }}>{ep.hostname}</strong>
                  <span className="col-muted" style={{ fontSize: 12 }}>{ep.os}</span>
                  <span className={`badge badge-${ep.status ?? 'offline'}`}>{ep.status ?? 'offline'}</span>
                </div>
                <TagsEditor
                  endpoint={ep}
                  groups={groupOptions}
                  saving={busy === ep.id}
                  onSave={payload => handleUpdateEndpoint(ep.id, payload)}
                />
              </div>
            ))
          }
        </section>
      </div>
    </AppShell>
  )
}
