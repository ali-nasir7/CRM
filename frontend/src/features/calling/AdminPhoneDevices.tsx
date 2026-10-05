import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ShieldOff, Smartphone } from 'lucide-react'
import { api } from '@/api/client'
import type { CallingDeviceItem, LiteUserItem } from '@/types'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { useCan } from '@/stores/auth'
import { fmtAgo } from '@/lib/utils'

const statusTone: Record<string, 'green' | 'red' | 'yellow' | 'gray'> = {
  ONLINE: 'green', OFFLINE: 'gray', BUSY: 'yellow', DISCONNECTED: 'red', REVOKED: 'red',
}

/**
 * Admin/manager visibility into every registered calling device in the organization - who owns
 * it, whether it is a Phone Agent or the legacy PC bridge, and whether it is online right now.
 * Reuses USER_VIEW (device ownership is user-management-adjacent data) rather than inventing a
 * dedicated permission the rest of RBAC would need to seed and assign to roles.
 */
export function AdminPhoneDevices() {
  const canView = useCan('USER_VIEW')
  const qc = useQueryClient()

  const { data, isLoading } = useQuery({
    queryKey: ['admin-calling-devices'],
    queryFn: async () => (await api.get<CallingDeviceItem[]>('/calling/admin/devices')).data,
    enabled: canView,
    refetchInterval: 30_000,
  })
  const { data: users } = useQuery({
    queryKey: ['users-lite'],
    queryFn: async () => (await api.get<LiteUserItem[]>('/users/lite')).data,
    enabled: canView,
  })

  const revoke = useMutation({
    mutationFn: async (id: string) => { await api.post(`/calling/admin/devices/${id}/revoke`) },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-calling-devices'] }),
  })

  if (!canView) return null

  const nameFor = (userId: string) => users?.find((u) => u.id === userId)?.displayName ?? userId.slice(0, 8)

  return (
    <div className="card mt-3 p-4">
      <p className="flex items-center gap-2 text-sm font-semibold text-slate-800">
        <Smartphone className="h-4 w-4 text-blue-600" /> Calling devices (organization)
      </p>
      <p className="mt-0.5 text-xs text-slate-500">Every phone registered to call through the CRM - Phone Agent and legacy bridge alike.</p>

      {isLoading ? (
        <p className="py-4 text-sm text-slate-500">Loading…</p>
      ) : !data || data.length === 0 ? (
        <p className="py-4 text-sm text-slate-500">No calling devices registered yet.</p>
      ) : (
        <div className="mt-3 space-y-2">
          {data.map((d) => (
            <div key={d.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-slate-100 px-3 py-2">
              <div>
                <p className="text-sm font-medium text-slate-800">
                  {d.deviceName}
                  <span className={`ml-1.5 rounded px-1.5 py-0.5 text-[10px] font-bold ${d.connectionMode === 'AGENT' ? 'bg-purple-50 text-purple-700' : 'bg-slate-100 text-slate-600'}`}>
                    {d.connectionMode === 'AGENT' ? 'PHONE AGENT' : 'LEGACY BRIDGE'}
                  </span>
                </p>
                <p className="text-xs text-slate-500">
                  {nameFor(d.userId)} · {d.connectionMode === 'AGENT' ? (d.deviceModel ?? d.platform) : d.platform}
                  {d.appVersion ? ` · v${d.appVersion}` : ''} · seen {d.lastSeenAt ? fmtAgo(d.lastSeenAt) : 'never'}
                </p>
              </div>
              <div className="flex items-center gap-1.5">
                <Badge tone={statusTone[d.status] ?? 'gray'}>{d.status}</Badge>
                {d.status !== 'REVOKED' && (
                  <Button variant="secondary" size="sm" loading={revoke.isPending} onClick={() => revoke.mutate(d.id)} title="Disconnect this phone permanently">
                    <ShieldOff className="h-3.5 w-3.5" /> Revoke
                  </Button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
