import { useState } from 'react'
import { UserPlus, Pencil, Trash2, X } from 'lucide-react'
import AppShell from '../components/AppShell'
import keycloak from '../keycloak'
import {
  useGetUsersQuery,
  useGetRolesQuery,
  useGetOrganizationsQuery,
  useCreateUserMutation,
  useCreateOrgUserMutation,
  useUpdateUserMutation,
  useDeleteUserMutation,
  useUpdateUserRolesMutation
} from '../api/pulseApi'

export default function UsersPage() {
  const isGlobalAdmin = !keycloak.tokenParsed?.org_id

  const { data: users = [], isLoading, refetch } = useGetUsersQuery()
  const { data: roles = [] } = useGetRolesQuery()
  const { data: orgs = [] } = useGetOrganizationsQuery(undefined, { skip: !isGlobalAdmin })
  const [createUser] = useCreateUserMutation()
  const [createOrgUser] = useCreateOrgUserMutation()
  const [updateUser] = useUpdateUserMutation()
  const [deleteUser] = useDeleteUserMutation()
  const [updateUserRoles] = useUpdateUserRolesMutation()

  const [showCreate, setShowCreate] = useState(false)
  const [editing, setEditing] = useState(null)
  const [confirmDelete, setConfirmDelete] = useState(null)
  const [error, setError] = useState(null)

  const [createForm, setCreateForm] = useState({
    username: '', email: '', firstName: '', lastName: '', password: '', roleName: '', orgId: ''
  })
  const [editForm, setEditForm] = useState({
    email: '', firstName: '', lastName: '', enabled: true, roleId: '', newPassword: ''
  })

  function handleCreateSubmit(e) {
    e.preventDefault()
    setError(null)

    const resetForm = () => setCreateForm({ username: '', email: '', firstName: '', lastName: '', password: '', roleName: '', orgId: '' })

    if (isGlobalAdmin) {
      if (!createForm.orgId) { setError('Select an organization'); return }
      const { orgId, ...body } = createForm
      if (!body.roleName) delete body.roleName
      createOrgUser({ orgId, ...body })
        .unwrap()
        .then(() => { setShowCreate(false); resetForm() })
        .catch(err => setError(err?.data?.message || 'Failed to create user'))
    } else {
      const { orgId, ...body } = createForm
      if (!body.roleName) delete body.roleName
      createUser(body)
        .unwrap()
        .then(() => { setShowCreate(false); resetForm() })
        .catch(err => setError(err?.data?.message || 'Failed to create user'))
    }
  }

  function openEdit(user) {
    const roleId = roles.find(r => user.roles?.includes(r.name))?.id || ''
    setEditForm({
      email: user.email || '',
      firstName: user.firstName || '',
      lastName: user.lastName || '',
      enabled: user.enabled,
      roleId,
      newPassword: ''
    })
    setEditing(user)
    setError(null)
  }

  function handleEditSubmit(e) {
    e.preventDefault()
    setError(null)
    const updateBody = {
      id: editing.id,
      email: editForm.email,
      firstName: editForm.firstName,
      lastName: editForm.lastName,
      enabled: editForm.enabled,
      newPassword: editForm.newPassword || null
    }
    const roleIds = editForm.roleId ? [editForm.roleId] : []

    Promise.all([
      updateUser(updateBody).unwrap(),
      updateUserRoles({ id: editing.id, roleIds }).unwrap()
    ])
      .then(() => setEditing(null))
      .catch(err => setError(err?.data?.message || 'Failed to update user'))
  }

  function handleDelete(userId) {
    deleteUser(userId)
      .unwrap()
      .then(() => setConfirmDelete(null))
      .catch(err => setError(err?.data?.message || 'Failed to delete user'))
  }

  return (
    <AppShell
      title="Users"
      actions={
        <button className="icon-btn endpoint-action" onClick={() => { setShowCreate(true); setError(null) }}>
          <UserPlus size={14} />New User
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
              <tr>
                <th>Username</th>
                <th>Email</th>
                <th>Status</th>
                <th>Role</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {users.map(user => (
                <tr key={user.id}>
                  <td style={{ fontWeight: 500 }}>{user.username}</td>
                  <td className="col-muted">{user.email}</td>
                  <td>
                    <span className={user.enabled ? 'badge-green' : 'badge-red'}>
                      {user.enabled ? 'Active' : 'Disabled'}
                    </span>
                  </td>
                  <td>
                    {user.roles?.length > 0
                      ? <span className="badge-blue">{user.roles[0]}</span>
                      : <span className="badge-gray">-</span>}
                  </td>
                  <td className="col-right">
                    {confirmDelete === user.id ? (
                      <span style={{ display: 'flex', gap: 6, justifyContent: 'flex-end', alignItems: 'center' }}>
                        <span className="col-muted" style={{ fontSize: 13 }}>Delete?</span>
                        <button className="icon-btn endpoint-action proc-kill-btn" onClick={() => handleDelete(user.id)}>Yes</button>
                        <button className="icon-btn endpoint-action" onClick={() => setConfirmDelete(null)}>No</button>
                      </span>
                    ) : (
                      <span style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
                        <button className="icon-btn endpoint-action" onClick={() => openEdit(user)}><Pencil size={12} />Edit</button>
                        <button className="icon-btn endpoint-action proc-kill-btn" onClick={() => setConfirmDelete(user.id)}><Trash2 size={12} />Delete</button>
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
            <div className="modal-header">
              <h2>New User</h2>
              <button type="button" className="modal-close" onClick={() => setShowCreate(false)}><X size={16} /></button>
            </div>
            <form onSubmit={handleCreateSubmit}>
              <div className="modal-body">
                <div className="form-field">
                  <label className="field-label">Username <span className="req">*</span></label>
                  <input required placeholder="johndoe" value={createForm.username}
                    onChange={e => setCreateForm({ ...createForm, username: e.target.value })} />
                </div>
                {isGlobalAdmin && (
                  <div className="form-field">
                    <label className="field-label">Organization <span className="req">*</span></label>
                    <select required value={createForm.orgId}
                      onChange={e => setCreateForm({ ...createForm, orgId: e.target.value })}>
                      <option value="">Select organization</option>
                      {orgs.map(o => <option key={o.id} value={o.id}>{o.name}</option>)}
                    </select>
                  </div>
                )}
                <div className="form-field">
                  <label className="field-label">Email</label>
                  <input type="email" placeholder="john@company.com" value={createForm.email}
                    onChange={e => setCreateForm({ ...createForm, email: e.target.value })} />
                </div>
                <div className="form-2col">
                  <div className="form-field">
                    <label className="field-label">First name</label>
                    <input placeholder="John" value={createForm.firstName}
                      onChange={e => setCreateForm({ ...createForm, firstName: e.target.value })} />
                  </div>
                  <div className="form-field">
                    <label className="field-label">Last name</label>
                    <input placeholder="Doe" value={createForm.lastName}
                      onChange={e => setCreateForm({ ...createForm, lastName: e.target.value })} />
                  </div>
                </div>
                <div className="form-field">
                  <label className="field-label">Password <span className="req">*</span></label>
                  <input required type="password" placeholder="Min 8 characters" value={createForm.password}
                    onChange={e => setCreateForm({ ...createForm, password: e.target.value })} />
                </div>
                <div className="form-field">
                  <label className="field-label">Role</label>
                  <select value={createForm.roleName}
                    onChange={e => setCreateForm({ ...createForm, roleName: e.target.value })}>
                    <option value="">No role</option>
                    {roles.map(r => <option key={r.id} value={r.name}>{r.name}</option>)}
                  </select>
                </div>
                {error && <p className="form-error">{error}</p>}
              </div>
              <div className="modal-actions">
                <button type="button" className="btn-secondary" onClick={() => setShowCreate(false)}>Cancel</button>
                <button type="submit" className="btn-primary">Create User</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {editing && (
        <div className="modal-overlay" onClick={() => setEditing(null)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h2>Edit {editing.username}</h2>
              <button type="button" className="modal-close" onClick={() => setEditing(null)}><X size={16} /></button>
            </div>
            <form onSubmit={handleEditSubmit}>
              <div className="modal-body">
                <div className="form-field">
                  <label className="field-label">Email</label>
                  <input type="email" placeholder="john@company.com" value={editForm.email}
                    onChange={e => setEditForm({ ...editForm, email: e.target.value })} />
                </div>
                <div className="form-2col">
                  <div className="form-field">
                    <label className="field-label">First name</label>
                    <input placeholder="John" value={editForm.firstName}
                      onChange={e => setEditForm({ ...editForm, firstName: e.target.value })} />
                  </div>
                  <div className="form-field">
                    <label className="field-label">Last name</label>
                    <input placeholder="Doe" value={editForm.lastName}
                      onChange={e => setEditForm({ ...editForm, lastName: e.target.value })} />
                  </div>
                </div>
                <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer', fontSize: 13 }}>
                  <input type="checkbox" checked={editForm.enabled}
                    onChange={e => setEditForm({ ...editForm, enabled: e.target.checked })} />
                  Active account
                </label>
                <div className="form-field">
                  <label className="field-label">Role</label>
                  <select value={editForm.roleId}
                    onChange={e => setEditForm({ ...editForm, roleId: e.target.value })}>
                    <option value="">No role</option>
                    {roles.map(r => <option key={r.id} value={r.id}>{r.name}</option>)}
                  </select>
                </div>
                <div className="form-field">
                  <label className="field-label">New password</label>
                  <input type="password" placeholder="Leave blank to keep current" value={editForm.newPassword}
                    onChange={e => setEditForm({ ...editForm, newPassword: e.target.value })} />
                </div>
                {error && <p className="form-error">{error}</p>}
              </div>
              <div className="modal-actions">
                <button type="button" className="btn-secondary" onClick={() => setEditing(null)}>Cancel</button>
                <button type="submit" className="btn-primary">Save Changes</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </AppShell>
  )
}
