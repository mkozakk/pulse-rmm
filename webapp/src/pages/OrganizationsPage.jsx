import { useState } from 'react'
import { Plus, Users, Trash2 } from 'lucide-react'
import AppShell from '../components/AppShell'
import {
  useGetOrganizationsQuery,
  useCreateOrganizationMutation,
  useDeleteOrganizationMutation,
  useGetOrgUsersQuery,
  useCreateOrgUserMutation,
  useGetRolesQuery
} from '../api/pulseApi'

function OrgUsersPanel({ org, onClose }) {
  const { data: users = [], isLoading } = useGetOrgUsersQuery(org.id)
  const { data: roles = [] } = useGetRolesQuery()
  const [createOrgUser] = useCreateOrgUserMutation()
  const [showAdd, setShowAdd] = useState(false)
  const [form, setForm] = useState({ username: '', email: '', firstName: '', lastName: '', password: '', roleName: '' })
  const [error, setError] = useState(null)

  function handleAdd(e) {
    e.preventDefault()
    setError(null)
    createOrgUser({ orgId: org.id, ...form, roleName: form.roleName || undefined })
      .unwrap()
      .then(() => {
        setShowAdd(false)
        setForm({ username: '', email: '', firstName: '', lastName: '', password: '', roleName: '' })
      })
      .catch(err => setError(err?.data?.detail || 'Failed to add user'))
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" style={{ minWidth: 560 }} onClick={e => e.stopPropagation()}>
        <h2>Users — {org.name}</h2>

        {isLoading ? <p className="panel-empty">Loading…</p> : (
          <table className="enrolment-table">
            <thead>
              <tr><th>Username</th><th>Email</th><th>Status</th></tr>
            </thead>
            <tbody>
              {users.length === 0
                ? <tr><td colSpan={3} className="col-muted" style={{ textAlign: 'center' }}>No users in this org yet</td></tr>
                : users.map(u => (
                    <tr key={u.id}>
                      <td style={{ fontWeight: 500 }}>{u.username}</td>
                      <td className="col-muted">{u.email}</td>
                      <td><span className={u.enabled ? 'badge-green' : 'badge-red'}>{u.enabled ? 'Active' : 'Disabled'}</span></td>
                    </tr>
                  ))
              }
            </tbody>
          </table>
        )}

        <div style={{ marginTop: 16 }}>
          {showAdd ? (
            <form onSubmit={handleAdd}>
              <label>Username *<input required value={form.username} onChange={e => setForm({ ...form, username: e.target.value })} /></label>
              <label>Email<input type="email" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} /></label>
              <label>First name<input value={form.firstName} onChange={e => setForm({ ...form, firstName: e.target.value })} /></label>
              <label>Last name<input value={form.lastName} onChange={e => setForm({ ...form, lastName: e.target.value })} /></label>
              <label>Password *<input required type="password" value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} /></label>
              <label>Role
                <select value={form.roleName} onChange={e => setForm({ ...form, roleName: e.target.value })}>
                  <option value="">- None -</option>
                  {roles.filter(r => r.name !== 'Global Admin').map(r => <option key={r.id} value={r.name}>{r.name}</option>)}
                </select>
              </label>
              {error && <p className="form-error">{error}</p>}
              <div className="modal-actions">
                <button type="submit" className="btn-primary">Add User</button>
                <button type="button" className="btn-secondary" onClick={() => setShowAdd(false)}>Cancel</button>
              </div>
            </form>
          ) : (
            <button className="icon-btn endpoint-action" onClick={() => setShowAdd(true)}><Plus size={13} />Add User</button>
          )}
        </div>

        <div className="modal-actions" style={{ marginTop: 24 }}>
          <button className="btn-secondary" onClick={onClose}>Close</button>
        </div>
      </div>
    </div>
  )
}

export default function OrganizationsPage() {
  const { data: orgs = [], isLoading } = useGetOrganizationsQuery()
  const [createOrganization] = useCreateOrganizationMutation()
  const [deleteOrganization] = useDeleteOrganizationMutation()

  const [showCreate, setShowCreate] = useState(false)
  const [name, setName] = useState('')
  const [error, setError] = useState(null)
  const [viewOrg, setViewOrg] = useState(null)
  const [confirmDelete, setConfirmDelete] = useState(null)

  function handleCreate(e) {
    e.preventDefault()
    setError(null)
    createOrganization({ name })
      .unwrap()
      .then(() => { setShowCreate(false); setName('') })
      .catch(err => setError(err?.data?.detail || 'Failed to create organization'))
  }

  function handleDelete(id) {
    deleteOrganization(id)
      .unwrap()
      .then(() => setConfirmDelete(null))
      .catch(err => setError(err?.data?.detail || 'Failed to delete organization'))
  }

  return (
    <AppShell
      title="Organizations"
      actions={
        <button className="icon-btn endpoint-action" onClick={() => { setShowCreate(true); setError(null) }}>
          <Plus size={14} />New Organization
        </button>
      }
    >
      {error && <p className="error-banner">{error}</p>}

      <section className="panel-card">
        {isLoading ? (
          <p className="panel-empty">Loading…</p>
        ) : (
          <table className="enrolment-table">
            <thead>
              <tr><th>Name</th><th>Created</th><th></th></tr>
            </thead>
            <tbody>
              {orgs.map(org => (
                <tr key={org.id}>
                  <td style={{ fontWeight: 500 }}>{org.name}</td>
                  <td className="col-muted" style={{ fontSize: 12 }}>{org.createdAt ? new Date(org.createdAt).toLocaleDateString() : '-'}</td>
                  <td className="col-right">
                    {confirmDelete === org.id ? (
                      <span style={{ display: 'flex', gap: 6, justifyContent: 'flex-end', alignItems: 'center' }}>
                        <span className="col-muted" style={{ fontSize: 13 }}>Delete?</span>
                        <button className="icon-btn endpoint-action proc-kill-btn" onClick={() => handleDelete(org.id)}>Yes</button>
                        <button className="icon-btn endpoint-action" onClick={() => setConfirmDelete(null)}>No</button>
                      </span>
                    ) : (
                      <span style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
                        <button className="icon-btn endpoint-action" onClick={() => setViewOrg(org)}><Users size={12} />Users</button>
                        <button className="icon-btn endpoint-action proc-kill-btn" onClick={() => setConfirmDelete(org.id)}><Trash2 size={12} />Delete</button>
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {showCreate && (
        <div className="modal-overlay" onClick={() => setShowCreate(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h2>New Organization</h2>
            <form onSubmit={handleCreate}>
              <label>Name *
                <input required value={name} onChange={e => setName(e.target.value)} />
              </label>
              {error && <p className="form-error">{error}</p>}
              <div className="modal-actions">
                <button type="submit" className="btn-primary">Create</button>
                <button type="button" className="btn-secondary" onClick={() => setShowCreate(false)}>Cancel</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {viewOrg && <OrgUsersPanel org={viewOrg} onClose={() => setViewOrg(null)} />}
    </AppShell>
  )
}
