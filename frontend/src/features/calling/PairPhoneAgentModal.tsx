import { useEffect, useRef, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, RefreshCw, Smartphone } from 'lucide-react'
import { api, apiError } from '@/api/client'
import type { PairingStartResponse } from '@/types'
import { Button } from '@/components/ui/Button'
import { Dialog } from '@/components/ui/Dialog'

/**
 * "Pair a new phone" flow: mint a 6-digit code, show it (with a countdown), and poll the
 * device list until a new AGENT device appears - which is the phone finishing the pairing
 * on its own screen. No QR code / deep link is required; the code is short enough to type.
 */
export function PairPhoneAgentModal({ open, onClose, knownDeviceIds }: {
  open: boolean
  onClose: () => void
  knownDeviceIds: string[]
}) {
  const qc = useQueryClient()
  const [pairing, setPairing] = useState<PairingStartResponse | null>(null)
  const [secondsLeft, setSecondsLeft] = useState(0)
  const [paired, setPaired] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const tickRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const start = useMutation({
    mutationFn: async () => (await api.post<PairingStartResponse>('/calling/pairing/start')).data,
    onSuccess: (data) => { setPairing(data); setError(null); setPaired(false) },
    onError: (e) => setError(apiError(e).message),
  })

  useEffect(() => {
    if (open && !pairing) start.mutate()
    if (!open) {
      setPairing(null); setPaired(false); setError(null)
      if (pollRef.current) clearInterval(pollRef.current)
      if (tickRef.current) clearInterval(tickRef.current)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  useEffect(() => {
    if (!pairing) return
    const expiresAt = new Date(pairing.expiresAt).getTime()
    tickRef.current = setInterval(() => {
      const left = Math.max(0, Math.round((expiresAt - Date.now()) / 1000))
      setSecondsLeft(left)
      if (left === 0 && tickRef.current) clearInterval(tickRef.current)
    }, 500)

    pollRef.current = setInterval(async () => {
      try {
        const devices = (await api.get('/calling/devices')).data as { id: string; connectionMode: string }[]
        const fresh = devices.find((d) => d.connectionMode === 'AGENT' && !knownDeviceIds.includes(d.id))
        if (fresh) {
          setPaired(true)
          qc.invalidateQueries({ queryKey: ['calling-devices'] })
          if (pollRef.current) clearInterval(pollRef.current)
          if (tickRef.current) clearInterval(tickRef.current)
        }
      } catch { /* keep polling */ }
    }, 2500)

    return () => {
      if (pollRef.current) clearInterval(pollRef.current)
      if (tickRef.current) clearInterval(tickRef.current)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pairing])

  const digits = (pairing?.code ?? '------').split('')
  const expired = pairing && secondsLeft === 0 && !paired

  return (
    <Dialog open={open} onClose={onClose} title="Pair CRM Phone Agent"
      description="Open the CRM Phone Agent app on the phone and enter this code.">
      <div className="space-y-4 text-center">
        {error && <div className="rounded-lg border border-red-200 bg-red-50 px-3.5 py-2.5 text-left text-sm text-red-700">{error}</div>}

        {paired ? (
          <div className="flex flex-col items-center gap-2 py-6">
            <CheckCircle2 className="h-10 w-10 text-green-600" />
            <p className="text-sm font-semibold text-slate-800">Phone connected</p>
            <p className="text-xs text-slate-500">Your calls will now dial through this device.</p>
            <Button className="mt-2" onClick={onClose}>Done</Button>
          </div>
        ) : (
          <>
            <div className="flex justify-center gap-1.5 py-3">
              {digits.map((d, i) => (
                <span key={i} className="flex h-12 w-9 items-center justify-center rounded-lg border border-slate-200 bg-slate-50 font-mono text-2xl font-bold text-slate-800">
                  {start.isPending ? '·' : d}
                </span>
              ))}
            </div>
            {pairing && !expired && (
              <p className="text-xs text-slate-500">Expires in {Math.floor(secondsLeft / 60)}:{String(secondsLeft % 60).padStart(2, '0')} · waiting for the phone…</p>
            )}
            {expired && <p className="text-xs font-medium text-amber-600">This code expired.</p>}
            <div className="flex items-center justify-center gap-2 pt-1">
              <Smartphone className="h-4 w-4 text-slate-400" />
              <p className="text-xs text-slate-500">Calls &gt; Pair Device &gt; enter the code above</p>
            </div>
            <Button variant="secondary" size="sm" loading={start.isPending} onClick={() => start.mutate()}>
              <RefreshCw className="h-3.5 w-3.5" /> {pairing ? 'Generate a new code' : 'Generate code'}
            </Button>
          </>
        )}
      </div>
    </Dialog>
  )
}
