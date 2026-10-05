import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { KeyRound, Plus, Trash2, UserCog } from 'lucide-react'
import { api, apiError } from '@/api/client'
import { useAuth, useCan } from '@/stores/auth'
import { useToast } from '@/components/ui/Toast'
import type { PageResponse, RoleItem, TeamItem, UserItem } from '@/types'
import { PageHeader } from '@/components/shared/PageHeader'
import { DataTable, type Column } from '@/components/shared/DataTable'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Dialog } from '@/components/ui/Dialog'
import { Input, Select, Label } from '@/components/ui/Input'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Avatar } from '@/components/ui/Misc'
import { fmtDate } from '@/lib/utils'
import { AdminPhoneDevices } from '@/features/calling/AdminPhoneDevices'

const STATUS_TONES: Record<string, 'green' | 'gray' | 'red' | 'yellow'> = { ACTIVE: 'green', INVITED: 'yellow', SUSPENDED: 'red', DISABLED: 'gray' }
const EMPTY = { email: '', password: '', firstName: '', lastName: '', jobTitle: '', phone: '', roleKeys: [] as string[], teamIds: [] as string[] }

export default function UsersPage() {
  const qc = useQueryClient()
  const toast = useToast()
  const { user: me } = useAuth()
  const canCreate = useCan('USER_CREATE')
  const canUpdate = useCan('USER_UPDATE')
  const canDelete = useCan('USER_DELETE')
  const [page, setPage] = useState(0)
  const [q, setQ] = useState('')
  const [createOpen, setCreateOpen] = useState(false)
  const [editUser, setEditUser] = useState<UserItem | null>(null)
  const [deleteUser, setDeleteUser] = useState<UserItem | null>(null)
  const [form, setForm] = useState(EMPTY)

  const { data, isFetching } = useQuery({
    queryKey: ['users', page, q],
    queryFn: async () => (await api.get<PageResponse<UserItem>>('/users', { params: { page, size: 25, q: q || undefined } })).data,
  })
  const { data: roles } = useQuery({ queryKey: ['roles'], queryFn: async () => (await api.get<RoleItem[]>('/roles')).data })
  const { data: teams } = useQuery({ queryKey: ['teams'], queryFn: async () => (await api.get<TeamItem[]>('/teams')).data })

  const create = useMutation({
    mutationFn: async () => (await api.post<UserItem>('/users', form)).data,
    onSuccess: (data: UserItem) => {
      qc.invalidateQueries({ queryKey: ['users'] }); qc.invalidateQueries({ queryKey: ['users-lite'] })
      if (data.tempPassword) {
        toast.push('info', `Email failed - temp password: ${data.tempPassword}`, 'SMTP is not reachable. Share this password manually; the user must change it at first login.')
      } else {
        toast.push('success', 'User created', data.tempPassword === null ? 'Onboarding email sent.' : undefined)
      }
      setCreateOpen(false); setForm(EMPTY)
    },
    onError: (e) => {
      const err = apiError(e)
      toast.push('error', 'Could not create user', err.message + (err.details ? ` — ${Object.values(err.details)[0]}` : ''))
    },
  })

  const update = useMutation({
    mutationFn: async (body: Record<string, unknown>) => api.put(`/users/${editUser!.id}`, body),
    onSuccess: () => { toast.push('success', 'User updated'); setEditUser(null); qc.invalidateQueries({ queryKey: ['users'] }); qc.invalidateQueries({ queryKey: ['users-lite'] }) },
    onError: (e) => toast.push('error', 'Update failed', apiError(e).message),
  })

  const del = useMutation({
    mutationFn: async (id: string) => api.delete(`/users/${id}`),
    onSuccess: () => { toast.push('success', 'User deactivated'); qc.invalidateQueries({ queryKey: ['users'] }); qc.invalidateQueries({ queryKey: ['users-lite'] }) },
  })

  const resetPassword = useMutation({
    mutationFn: async (id: string) => (await api.post<{ tempPassword: string }>(`/users/${id}/reset-password`, { sendEmail: false })).data,
    onSuccess: (data: { tempPassword: string }) => toast.push('success', `Temp password: ${data.tempPassword}`, 'Share it over a secure channel. The user should change it after login.'),
    onError: (e) => toast.push('error', 'Failed', apiError(e).message),
  })

  const toggleRole = (key: string) => {
    setForm((f) => ({ ...f, roleKeys: f.roleKeys.includes(key) ? f.roleKeys.filter((r) => r !== key) : [...f.roleKeys, key] }))
  }

  const columns: Column<UserItem>[] = [
    { key: 'user', header: 'User', render: (u) => (
      <div className="flex items-center gap-2.5">
        <Avatar name={u.displayName} />
        <div>
          <p className="font-medium text-slate-800">{u.displayName}{u.id === me?.id && <span className="ml-1 text-xs text-slate-400">(you)</span>}</p>
          <p className="text-xs text-slate-500">{u.email}</p>
        </div>
      </div>
    ) },
    { key: 'roles', header: 'Roles', render: (u) => <div className="flex flex-wrap gap-1">{u.roleKeys.map((r) => <Badge key={r} tone="blue">{r.replace('_', ' ')}</Badge>)}</div> },
    { key: 'teams', header: 'Teams', render: (u) => <span className="text-slate-600">{u.teams.map((t) => t.name).join(', ') || '—'}</span> },
    { key: 'status', header: 'Status', render: (u) => <Badge tone={STATUS_TONES[u.status] ?? 'gray'}>{u.status}</Badge> },
    { key: 'lastLogin', header: 'Last login', render: (u) => <span className="text-slate-500">{u.lastLoginAt ? fmtDate(u.lastLoginAt) : 'Never'}</span> },
    {
      key: 'actions', header: '', render: (u) => (
        <div className="flex justify-end gap-1">
          {canUpdate && <Button variant="ghost" size="icon" title="Edit" onClick={(e) => { e.stopPropagation(); setEditUser(u) }}><UserCog className="h-3.5 w-3.5" /></Button>}
          {canDelete && u.id !== me?.id && <Button variant="ghost" size="icon" title="Deactivate" onClick={(e) => { e.stopPropagation(); setDeleteUser(u) }}><Trash2 className="h-3.5 w-3.5 text-red-400" /></Button>}
        </div>
      ),
    },
  ]

  return (
    <div>
      <PageHeader title="Users" subtitle="Create users, assign roles and teams." actions={canCreate ? <Button onClick={() => setCreateOpen(true)}><Plus className="h-4 w-4" /> New user</Button> : undefined} />
      <div className="card mb-3 p-3">
        <Input value={q} onChange={(e) => { setQ(e.target.value); setPage(0) }} placeholder="Search users…" className="max-w-md" />
      </div>
      <div className="card">
        <DataTable data={data} loading={isFetching} columns={columns} onPageChange={setPage} onRowClick={(u) => canUpdate && setEditUser(u)} />
      </div>

      <AdminPhoneDevices />

      {/* Create */}
      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} title="New user" wide>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div>
            <Label required>Email</Label>
            <Input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
          </div>
          <div>
            <Label required>Temp password</Label>
            <Input type="text" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} placeholder="Min 10 chars, letter + digit" />
          </div>
          <div>
            <Label required>First name</Label>
            <Input value={form.firstName} onChange={(e) => setForm({ ...form, firstName: e.target.value })} />
          </div>
          <div>
            <Label required>Last name</Label>
            <Input value={form.lastName} onChange={(e) => setForm({ ...form, lastName: e.target.value })} />
          </div>
          <div>
            <Label>Job title</Label>
            <Input value={form.jobTitle} onChange={(e) => setForm({ ...form, jobTitle: e.target.value })} />
          </div>
          <div>
            <Label>Phone</Label>
            <Input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
          </div>
        </div>
        <div className="mt-3">
          <Label required>Roles</Label>
          <div className="flex flex-wrap gap-1.5">
            {roles?.map((r) => (
              <button key={r.key} type="button" onClick={() => toggleRole(r.key)}
                className={`rounded-full px-3 py-1 text-xs font-medium ring-1 transition-colors ${form.roleKeys.includes(r.key) ? 'bg-blue-600 text-white ring-blue-600' : 'bg-white text-slate-600 ring-slate-200 hover:ring-blue-300'}`}>
                {r.name}
              </button>
            ))}
          </div>
          <p className="mt-1 text-xs text-slate-400">Data visibility follows the role: rep sees own, manager sees team, admin sees all.</p>
        </div>
        <div className="mt-3">
          <Label>Teams</Label>
          <Select value="" onChange={(e) => { if (e.target.value) setForm((f) => ({ ...f, teamIds: [...new Set([...f.teamIds, e.target.value])] })) }}>
            <option value="">Add to team…</option>
            {teams?.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
          </Select>
          <div className="mt-1.5 flex flex-wrap gap-1">
            {form.teamIds.map((id) => {
              const t = teams?.find((x) => x.id === id)
              return (
                <button key={id} type="button" onClick={() => setForm((f) => ({ ...f, teamIds: f.teamIds.filter((x) => x !== id) }))}
                  className="rounded-full bg-slate-100 px-2.5 py-0.5 text-xs text-slate-600">{t?.name} ×</button>
              )
            })}
          </div>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setCreateOpen(false)}>Cancel</Button>
          <Button disabled={!form.email || !form.password || !form.firstName || form.roleKeys.length === 0} loading={create.isPending} onClick={() => create.mutate()}>Create user</Button>
        </div>
      </Dialog>

      {/* Edit */}
      <Dialog open={!!editUser} onClose={() => setEditUser(null)} title={`Edit — ${editUser?.displayName ?? ''}`} wide>
        {editUser && (
          <>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div>
                <Label>Job title</Label>
                <Input defaultValue={editUser.jobTitle ?? ''} onChange={(e) => (editUser.jobTitle = e.target.value)} />
              </div>
              <div>
                <Label>Phone</Label>
                <Input defaultValue={editUser.phone ?? ''} onChange={(e) => (editUser.phone = e.target.value)} />
              </div>
            </div>
            <div className="mt-3">
              <Label>Roles</Label>
              <div className="flex flex-wrap gap-1.5">
                {roles?.map((r) => {
                  const active = editUser.roleKeys.includes(r.key)
                  return (
                    <button key={r.key} type="button"
                      onClick={() => setEditUser({ ...editUser, roleKeys: active ? editUser.roleKeys.filter((k) => k !== r.key) : [...editUser.roleKeys, r.key] })}
                      className={`rounded-full px-3 py-1 text-xs font-medium ring-1 ${active ? 'bg-blue-600 text-white ring-blue-600' : 'bg-white text-slate-600 ring-slate-200'}`}>
                      {r.name}
                    </button>
                  )
                })}
              </div>
            </div>
            <div className="mt-3">
              <Label>Teams</Label>
              <div className="flex flex-wrap gap-1.5">
                {teams?.map((t) => {
                  const active = editUser.teams.some((x) => x.id === t.id)
                  return (
                    <button key={t.id} type="button"
                      onClick={() => setEditUser({ ...editUser, teams: active ? editUser.teams.filter((x) => x.id !== t.id) : [...editUser.teams, { id: t.id, name: t.name }] })}
                      className={`rounded-full px-3 py-1 text-xs font-medium ring-1 ${active ? 'bg-slate-800 text-white ring-slate-800' : 'bg-white text-slate-600 ring-slate-200'}`}>
                      {t.name}
                    </button>
                  )
                })}
              </div>
            </div>
            <div className="mt-5 flex items-center justify-between">
              <Button variant="secondary" size="sm" loading={resetPassword.isPending} onClick={() => resetPassword.mutate(editUser.id)}><KeyRound className="h-3.5 w-3.5" /> Reset password</Button>
              <div className="flex gap-2">
                <Button variant="secondary" onClick={() => setEditUser(null)}>Cancel</Button>
                <Button loading={update.isPending} onClick={() => update.mutate({ jobTitle: editUser.jobTitle, phone: editUser.phone, roleKeys: editUser.roleKeys, teamIds: editUser.teams.map((t) => t.id) })}>Save changes</Button>
              </div>
            </div>
          </>
        )}
      </Dialog>

      <ConfirmDialog
        open={!!deleteUser}
        onClose={() => setDeleteUser(null)}
        onConfirm={async () => { if (deleteUser) await del.mutateAsync(deleteUser.id) }}
        title={`Deactivate ${deleteUser?.displayName}?`}
        message="The user can no longer sign in. Their records, assignments, and audit history are preserved."
        confirmLabel="Deactivate user"
        danger
      />
    </div>
  )
}
