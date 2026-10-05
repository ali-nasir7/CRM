import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { PhoneCall, PhoneOff } from 'lucide-react'
import { api, apiError } from '@/api/client'
import type { CallStateItem, CallingDeviceItem } from '@/types'
import { Button } from '@/components/ui/Button'
import { Dialog } from '@/components/ui/Dialog'
import { Input, Select, Label, Textarea } from '@/components/ui/Input'
import { Badge } from '@/components/ui/Badge'

const OUTCOMES = ['NO_ANSWER', 'BUSY', 'WRONG_NUMBER', 'INTERESTED', 'NOT_INTERESTED', 'CALL_BACK_LATER', 'MEETING_BOOKED']
const LIVE: Record<string, 'blue' | 'green' | 'red' | 'yellow' | 'gray'> = {
  INITIATING: 'blue', RINGING: 'blue', CONNECTED: 'green', ENDED: 'gray', NO_ANSWER: 'yellow', BUSY: 'yellow', FAILED: 'red',
}

/**
 * Places a call through the logged-in user's OWN registered device (their Android + SIM via
 * the bridge). Shows live state + a timer while the call runs, then captures the outcome,
 * notes and an optional follow-up task for "Call back".
 */
export function CallNowModal({ open, onClose, leadId, leadName, defaultNumber }: {
  open: boolean
  onClose: () => void
  leadId?: string
  leadName?: string
  defaultNumber?: string
}) {
  const qc = useQueryClient()
  const [number, setNumber] = useState(defaultNumber ?? '')
  const [deviceId, setDeviceId] = useState('')
  const [activeCall, setActiveCall] = useState<CallStateItem | null>(null)
  const [outcome, setOutcome] = useState('')
  const [notes, setNotes] = useState('')
  const [followUp, setFollowUp] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [seconds, setSeconds] = useState(0)
  const timer = useRef<ReturnType<typeof setInterval> | null>(null)

  const { data: devices } = useQuery({
    queryKey: ['calling-devices'],
    queryFn: async () => (await api.get<CallingDeviceItem[]>('/calling/devices')).data,
    enabled: open,
  })

  // live state polling while a call exists
  useEffect(() => {
    if (!activeCall || ['ENDED'].includes(activeCall.status)) return
    const poll = setInterval(async () => {
      try {
        const s = await api.get<CallStateItem>(`/calling/calls/${activeCall.id}`)
        setActiveCall(s.data)
        if (s.data.status === 'CONNECTED') {
          if (!timer.current) timer.current = setInterval(() => setSeconds((x) => x + 1), 1000)
        }
        if (s.data.status === 'ENDED') {
          setOutcome(['CONNECTED'].includes(s.data.outcome ?? '') ? 'INTERESTED' : (s.data.outcome ?? ''))
        }
      } catch { /* keep polling; the backend may be restarting */ }
    }, 2000)
    return () => clearInterval(poll)
  }, [activeCall])

  useEffect(() => () => { if (timer.current) clearInterval(timer.current) }, [])

  const start = useMutation({
    mutationFn: async () => (await api.post<CallStateItem>('/calling/calls', {
      leadId: leadId || undefined, number, deviceId: deviceId || undefined,
    })).data,
    onSuccess: (c) => { setActiveCall(c); setError(null); setSeconds(0) },
    onError: (e) => setError(apiError(e).message),
  })

  const endCall = useMutation({
    mutationFn: async () => (await api.post<CallStateItem>(`/calling/calls/${activeCall!.id}/end`)).data,
    onSuccess: (c) => { setActiveCall(c); setError(null) },
    onError: (e) => setError(apiError(e).message),
  })

  const finish = useMutation({
    mutationFn: async () => (await api.patch<CallStateItem>(`/calling/calls/${activeCall!.id}`, {
      outcome, notes, createFollowUp: followUp && outcome === 'CALL_BACK_LATER',
    })).data,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['calls'] })
      qc.invalidateQueries({ queryKey: ['lead-calls'] })
      qc.invalidateQueries({ queryKey: ['lead-timeline'] })
      qc.invalidateQueries({ queryKey: ['lead-tasks'] })
      qc.invalidateQueries({ queryKey: ['calling-analytics'] })
      close()
    },
    onError: (e) => setError(apiError(e).message),
  })

  const close = () => {
    if (timer.current) clearInterval(timer.current)
    setActiveCall(null); setOutcome(''); setNotes(''); setFollowUp(false); setSeconds(0); setError(null)
    onClose()
  }

  const running = activeCall && ['INITIATING', 'RINGING', 'CONNECTED'].includes(activeCall.status)
  const selectedDevice = (devices ?? []).find((d) => d.id === deviceId)
    ?? (devices ?? []).find((d) => d.isDefault)
    ?? (devices ?? [])[0]
  // BRIDGE (legacy adb bridge + Windows Phone Link) genuinely mirrors call audio to the PC over
  // Bluetooth. AGENT (the CRM Phone Agent companion app) places a real SIM call with ACTION_CALL -
  // there is no audio path to the PC at all, so saying otherwise here would send reps into a call
  // listening to a PC headset that was never going to carry any audio.
  const audioHint = selectedDevice?.connectionMode === 'BRIDGE'
    ? 'Placed from YOUR registered device. Your PC headset handles audio via your phone link.'
    : 'Placed from YOUR phone’s own SIM. Talk and listen on the PHONE itself (its earpiece/speaker, or a Bluetooth headset paired to the PHONE) - the PC does not carry call audio.'

  return (
    <Dialog open={open} onClose={close} wide
      title={activeCall ? (running ? 'Call in progress' : 'Call finished') : `Call ${leadName ?? 'number'}`}
      description={activeCall ? undefined : audioHint}>
      <div className="space-y-4">
        {error && <div className="rounded-lg border border-red-200 bg-red-50 px-3.5 py-2.5 text-sm text-red-700">{error}</div>}

        {!activeCall && (
          <>
            <div>
              <Label htmlFor="call-number" required>Phone number</Label>
              <Input id="call-number" value={number} onChange={(e) => setNumber(e.target.value)} placeholder="+923001234567" />
            </div>
            <div>
              <Label htmlFor="call-device">Calling device</Label>
              <Select id="call-device" value={deviceId} onChange={(e) => setDeviceId(e.target.value)}>
                <option value="">Default device</option>
                {(devices ?? []).map((d) => (
                  <option key={d.id} value={d.id}>{d.deviceName} ({d.status})</option>
                ))}
              </Select>
              {(devices ?? []).length === 0 && (
                <p className="mt-1 text-xs text-amber-600">No device registered yet - add one under Calls &gt; My calling devices.</p>
              )}
              {(devices ?? []).length > 0 && !(devices ?? []).some((d) => d.status === 'ONLINE') && (
                <p className="mt-1 text-xs text-amber-600">
                  None of your devices is ONLINE. Start the bridge on the PC next to your phone:
                  node bridge/android-bridge.js (setup: bridge/README.md).
                </p>
              )}
            </div>
            <Button className="w-full" loading={start.isPending} disabled={!number.trim()} onClick={() => start.mutate()}>
              <PhoneCall className="h-4 w-4" /> Place call
            </Button>
          </>
        )}

        {activeCall && (
          <>
            <div className="flex items-center justify-between rounded-lg bg-slate-50 px-4 py-3">
              <div>
                <p className="text-sm font-semibold text-slate-800">{leadName ?? number}</p>
                <p className="text-xs text-slate-500">{activeCall.number}</p>
              </div>
              <div className="text-right">
                <Badge tone={LIVE[activeCall.status] ?? 'gray'}>{activeCall.status}</Badge>
                <p className="mt-1 font-mono text-lg font-bold tabular-nums text-slate-800">
                  {String(Math.floor(seconds / 60)).padStart(2, '0')}:{String(seconds % 60).padStart(2, '0')}
                </p>
              </div>
            </div>

            {running && (
              <>
                <p className="text-xs text-slate-500">{audioHint}</p>
                <Button
                  variant="danger" className="w-full"
                  loading={endCall.isPending} onClick={() => endCall.mutate()}
                >
                  <PhoneOff className="h-4 w-4" /> End call
                </Button>
              </>
            )}

            {activeCall.status === 'ENDED' && (
              <>
                <div>
                  <Label required>Outcome</Label>
                  <Select value={outcome} onChange={(e) => setOutcome(e.target.value)}>
                    <option value="">Select outcome…</option>
                    {OUTCOMES.map((o) => <option key={o} value={o}>{o.replace(/_/g, ' ')}</option>)}
                  </Select>
                </div>
                <div>
                  <Label>Notes</Label>
                  <Textarea rows={3} value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="What was discussed?" />
                </div>
                {outcome === 'CALL_BACK_LATER' && (
                  <label className="flex items-center gap-2 text-sm text-slate-700">
                    <input type="checkbox" checked={followUp} onChange={(e) => setFollowUp(e.target.checked)} />
                    Create a follow-up task
                  </label>
                )}
                <Button className="w-full" loading={finish.isPending} disabled={!outcome} onClick={() => finish.mutate()}>Save outcome</Button>
              </>
            )}
          </>
        )}
      </div>
    </Dialog>
  )
}
