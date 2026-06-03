import { useState } from 'react'
import { UserPlus, Pencil, Trash2 } from 'lucide-react'
import AppShell from '../components/AppShell'
import {
  useGetUsersQuery,
  useGetRolesQuery,
  useCreateUserMutation,
  useUpdateUserMutation,
  useDeleteUserMutation,
  useUpdateUserRolesMutation
} from '../api/pulseApi'

export default function UsersPage() {
  const { data: users = [], isLoading, refetch } = useGetUsersQuery()
  const { data: roles = [] } = useGetRolesQuery()
  const [createUser] = useCreateUserMutation()
  const [updateUser] = useUpdateUserMutation()
  const [deleteUser] = useDeleteUserMutation()
  const [updateUserRoles] = useUpdateUserRolesMutation()

  const [showCreate, setShowCreate] = useState(false)
  const [editing, setEditing] = useState(null)
  const [confirmDelete, setConfirmDelete] = useState(null)
  const [error, setError] = useState(null)

  const [createForm, setCreateForm] = useState({
    username: '', email: '', firstName: '', lastName: '', password: '', roleName: ''
  })
  const [editForm, setEditForm] = useState({
    email: '', firstName: '', lastName: '', enabled: true, roleId: '', newPassword: ''
  })

  function handleCreateSubmit(e) {
    e.preventDefault()
    setError(null)
    const body = { ...createForm }
    if (!body.roleName) delete body.roleName
    createUser(body)
      .unwrap()
      .then(() => {
        setShowCreate(false)
        setCreateForm({ username: '', email: '', firstName: '', lastName: '', password: '', roleName: '' })
      })
      .catch(err => setError(err?.data?.message || 'Failed to create user'))
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
        <button className="btn-primary icon-btn" onClick={() => { setShowCreate(true); setError(null) }}>
          <UserPlus size={14} />New User
        </button>
      }
    >
      {error && <p className="error-banner">{error}</p>}

      {isLoading ? (
        <p>Loading…</p>
      ) : (
        <table className="data-table">
          <thead>
            <tr>
              <th>Username</th>
              <th>Email</th>
              <th>Status</th>
              <th>Role</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {users.map(user => (
              <tr key={user.id}>
                <td>{user.username}</td>
                <td>{user.email}</td>
                <td>
                  <span className={user.enabled ? 'badge-green' : 'badge-red'}>
                    {user.enabled ? 'Active' : 'Disabled'}
                  </span>
                </td>
                <td>
                  {user.roles?.length > 0
                    ? <span className="badge-blue">{user.roles[0]}</span>
                    : <span className="badge-gray">—</span>}
                </td>
                <td>
                  {confirmDelete === user.id ? (
                    <span>
                      Delete?{' '}
                      <button className="btn-danger-sm" onClick={() => handleDelete(user.id)}>Yes</button>
                      {' '}
                      <button className="btn-sm" onClick={() => setConfirmDelete(null)}>No</button>
                    </span>
                  ) : (
                    <>
                      <button className="btn-sm icon-btn" onClick={() => openEdit(user)}><Pencil size={12} />Edit</button>
                      {' '}
                      <button className="btn-danger-sm icon-btn" onClick={() => setConfirmDelete(user.id)}><Trash2 size={12} />Delete</button>
                    </>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {showCreate && (
        <div className="modal-overlay" onClick={() => setShowCreate(false)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h2>New User</h2>
            <form onSubmit={handleCreateSubmit}>
              <label>Username *
                <input required value={createForm.username}
                  onChange={e => setCreateForm({ ...createForm, username: e.target.value })} />
              </label>
              <label>Email
                <input type="email" value={createForm.email}
                  onChange={e => setCreateForm({ ...createForm, email: e.target.value })} />
              </label>
              <label>First name
                <input value={createForm.firstName}
                  onChange={e => setCreateForm({ ...createForm, firstName: e.target.value })} />
              </label>
              <label>Last name
                <input value={createForm.lastName}
                  onChange={e => setCreateForm({ ...createForm, lastName: e.target.value })} />
              </label>
              <label>Password *
                <input required type="password" value={createForm.password}
                  onChange={e => setCreateForm({ ...createForm, password: e.target.value })} />
              </label>
              <label>Role
                <select value={createForm.roleName}
                  onChange={e => setCreateForm({ ...createForm, roleName: e.target.value })}>
                  <option value="">— None —</option>
                  {roles.map(r => <option key={r.id} value={r.name}>{r.name}</option>)}
                </select>
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

      {editing && (
        <div className="modal-overlay" onClick={() => setEditing(null)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <h2>Edit {editing.username}</h2>
            <form onSubmit={handleEditSubmit}>
              <label>Email
                <input type="email" value={editForm.email}
                  onChange={e => setEditForm({ ...editForm, email: e.target.value })} />
              </label>
              <label>First name
                <input value={editForm.firstName}
                  onChange={e => setEditForm({ ...editForm, firstName: e.target.value })} />
              </label>
              <label>Last name
                <input value={editForm.lastName}
                  onChange={e => setEditForm({ ...editForm, lastName: e.target.value })} />
              </label>
              <label>
                <input type="checkbox" checked={editForm.enabled}
                  onChange={e => setEditForm({ ...editForm, enabled: e.target.checked })} />
                {' '}Enabled
              </label>
              <label>Role
                <select value={editForm.roleId}
                  onChange={e => setEditForm({ ...editForm, roleId: e.target.value })}>
                  <option value="">— None —</option>
                  {roles.map(r => <option key={r.id} value={r.id}>{r.name}</option>)}
                </select>
              </label>
              <label>New password (leave blank to keep)
                <input type="password" value={editForm.newPassword}
                  onChange={e => setEditForm({ ...editForm, newPassword: e.target.value })} />
              </label>
              {error && <p className="form-error">{error}</p>}
              <div className="modal-actions">
                <button type="submit" className="btn-primary">Save</button>
                <button type="button" className="btn-secondary" onClick={() => setEditing(null)}>Cancel</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </AppShell>
  )
}
