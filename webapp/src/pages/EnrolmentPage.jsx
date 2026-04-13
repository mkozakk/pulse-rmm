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
      {label && <p style={{ marginBottom: 4, fontSize: 13 }}>{label}</p>}
      <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
        <code style={{ flex: 1, padding: '6px 8px', background: '#1a1a2e', borderRadius: 4, fontSize: 12, wordBreak: 'break-all' }}>
          {text}
        </code>
        <button className="icon-btn" onClick={copy} style={{ whiteSpace: 'nowrap' }}>
          {copied ? <><Check size={13} />Copied!</> : <><Copy size={13} />Copy</>}
        </button>
      </div>
    </div>
  )
}

function InstallCommands({ token }) {
  return (
    <div className="stack" style={{ marginTop: 8 }}>
      <p className="panel-empty">Token {token.id} · expires {token.expiresAt}</p>
      <CopyLine label="Linux (Debian/Ubuntu/Fedora/RHEL) — run as root:" text={token.installSh} />
      <CopyLine label="Windows — PowerShell as Administrator:" text={token.installPs1} />
    </div>
  )
}

function TagsEditor({ endpoint, groups, onSave, saving }) {
  const [groupId, setGroupId] = useState(endpoint.groupId ?? '')
  const [tagsText, setTagsText] = useState((endpoint.tags ?? []).map(tag => `${tag.key}=${tag.value}`).join(', '))

  async function handleSave() {
    const tags = tagsText
      .split(',')
      .map(tag => tag.trim())
      .filter(Boolean)
      .map(tag => {
        const [key, ...rest] = tag.split('=')
        return { key: key.trim(), value: rest.join('=').trim() }
      })

    await onSave({ groupId, tags })
  }

  return (
    <div className="form-grid enrolment-inline-form">
      <select value={groupId} onChange={e => setGroupId(e.target.value)}>
        <option value="">No group</option>
        {groups.map(group => (
          <option key={group.id} value={group.id}>{group.name}</option>
        ))}
      </select>
      <input value={tagsText} onChange={e => setTagsText(e.target.value)} placeholder="env=prod, site=warsaw" />
      <button onClick={handleSave} disabled={saving}>Save</button>
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
    } finally {
      setBusy('')
    }
  }

  async function handleCreateGroup() {
    setBusy('group')
    try {
      await createGroup({ name: groupName, parentId: parentId || null }).unwrap()
      setGroupName('')
      setParentId('')
    } finally {
      setBusy('')
    }
  }

  async function handleCreateRule() {
    setBusy('rule')
    try {
      await createRule(rule).unwrap()
      setRule({ conditionField: 'hostname', conditionValue: '', tagKey: '', tagValue: '' })
    } finally {
      setBusy('')
    }
  }

  async function handleUpdateEndpoint(endpointId, payload) {
    setBusy(endpointId)
    try {
      if (payload.groupId !== undefined) await updateGroup({ id: endpointId, groupId: payload.groupId || null }).unwrap()
      if (payload.tags !== undefined) await updateTags({ id: endpointId, tags: payload.tags }).unwrap()
    } finally {
      setBusy('')
    }
  }

  return (
    <AppShell title="Enrolment" subtitle="Groups, tokens, tag rules, and endpoint assignment.">
      <section className="panel-card stack">
        <h2 className="section-title">Create enrolment token</h2>
        <div className="form-grid">
          <select value={tokenGroupId} onChange={e => setTokenGroupId(e.target.value)}>
            <option value="">Select group</option>
            {groupOptions.map(group => (
              <option key={group.id} value={group.id}>{group.name}</option>
            ))}
          </select>
          <input value={tokenTtl} onChange={e => setTokenTtl(e.target.value)} type="number" min="1" placeholder="TTL hours" />
          <button className="icon-btn" onClick={handleCreateToken} disabled={busy === 'token'}><Key size={14} />Create token</button>
        </div>
        {tokenResult && <InstallCommands token={tokenResult} />}
      </section>

      <section className="panel-card stack">
        <h2 className="section-title">Groups</h2>
        <div className="form-grid">
          <input value={groupName} onChange={e => setGroupName(e.target.value)} placeholder="Group name" />
          <select value={parentId} onChange={e => setParentId(e.target.value)}>
            <option value="">No parent</option>
            {groupOptions.map(group => (
              <option key={group.id} value={group.id}>{group.name}</option>
            ))}
          </select>
          <button className="icon-btn" onClick={handleCreateGroup} disabled={busy === 'group'}><FolderPlus size={14} />Create group</button>
        </div>
        <div className="list-card">
          {groupOptions.map(group => (
            <div key={group.id} className="list-row">
              <span>{group.name}</span>
              <span className="muted">{group.parentId ?? 'root'}</span>
            </div>
          ))}
        </div>
      </section>

      <section className="panel-card stack">
        <h2 className="section-title">Tag rules</h2>
        <div className="form-grid">
          <input value={rule.conditionField} onChange={e => setRule({ ...rule, conditionField: e.target.value })} placeholder="condition field" />
          <input value={rule.conditionValue} onChange={e => setRule({ ...rule, conditionValue: e.target.value })} placeholder="condition value" />
          <input value={rule.tagKey} onChange={e => setRule({ ...rule, tagKey: e.target.value })} placeholder="tag key" />
          <input value={rule.tagValue} onChange={e => setRule({ ...rule, tagValue: e.target.value })} placeholder="tag value" />
          <button className="icon-btn" onClick={handleCreateRule} disabled={busy === 'rule'}><Tag size={14} />Create rule</button>
          <button className="icon-btn" onClick={() => evaluateRules().unwrap()} disabled={busy === 'rule'}><RefreshCw size={14} />Evaluate</button>
        </div>
        <div className="list-card">
          {(tagRules.data ?? []).map(item => (
            <div key={item.id} className="list-row">
              <span>{item.conditionField} = {item.conditionValue}</span>
              <span className="muted">{item.tagKey}={item.tagValue}</span>
            </div>
          ))}
        </div>
      </section>

      <section className="panel-card stack">
        <h2 className="section-title">Endpoints</h2>
        <div className="list-card">
          {endpointRows.map(endpoint => (
            <div key={endpoint.id} className="stack">
              <div className="list-row">
                <strong>{endpoint.hostname}</strong>
                <span className="muted">{endpoint.os} · {endpoint.status}</span>
              </div>
              <TagsEditor
                endpoint={endpoint}
                groups={groupOptions}
                saving={busy === endpoint.id}
                onSave={(payload) => handleUpdateEndpoint(endpoint.id, payload)}
              />
            </div>
          ))}
        </div>
      </section>
    </AppShell>
  )
}
