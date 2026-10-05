import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import  { CheckCircle2, Download, FileSpreadsheet, History, Loader2, Upload }  from 'lucide-react'
import { api, apiError } from '@/api/client'
import { useToast } from '@/components/ui/Toast'
import { Dialog } from '@/components/ui/Dialog'
import { Button } from '@/components/ui/Button'
import  { Select }  from '@/components/ui/Input'
import { Badge } from '@/components/ui/Badge'
import { Table, THead, TR, TH, TD } from '@/components/ui/Table'
import { EmptyState } from '@/components/ui/Misc'
import { fmtDateTime } from '@/lib/utils'
import type  { ImportJobItem, ImportRowItem, CustomFieldItem, PageResponse }  from '@/types'

const TARGETS: { key: string; label: string; required?: boolean }[] = [
  { key: 'business_name', label: 'Business name', required: true },
  { key: 'first_name', label: 'First name' },
  { key: 'last_name', label: 'Last name' },
  { key: 'job_title', label: 'Job title' },
  { key: 'email', label: 'Email' },
  { key: 'phone', label: 'Phone' },
  { key: 'whatsapp', label: 'WhatsApp' },
  { key: 'website', label: 'Website' },
  { key: 'linkedin', label: 'LinkedIn' },
  { key: 'country', label: 'Country' },
  { key: 'state', label: 'State' },
  { key: 'city', label: 'City' },
  { key: 'address', label: 'Address' },
  { key: 'industry', label: 'Industry' },
  { key: 'business_type', label: 'Business type' },
  { key: 'company_size', label: 'Company size' },
]

export function ImportWizard({ open, onClose }: { open: boolean; onClose: () => void }) {
  const qc = useQueryClient()
  const toast = useToast()
  const fileRef = useRef<HTMLInputElement>(null)
  const [jobId, setJobId] = useState<string | null>(null)
  const [tab, setTab] = useState<'history' | 'wizard'>('wizard')

  const { data: fields } = useQuery({ queryKey: ['custom-fields'], queryFn: async () => (await api.get<CustomFieldItem[]>('/custom-fields')).data, enabled: open })

  const targets: { key: string; label: string; required?: boolean }[] = [...TARGETS, ...(fields ?? []).map((f) => ({ key: `cf_${f.key}`, label: `Custom: ${f.label}` }))]

  const { data: job, refetch: refetchJob } = useQuery({
    queryKey: ['import', jobId],
    queryFn: async () => (await api.get<ImportJobItem>(`/imports/${jobId}`)).data,
    enabled: !!jobId,
    refetchInterval: (q) => (q.state.data && ['PROCESSING', 'AWAITING_MAPPING'].includes(q.state.data.status) ? 1500 : false),
  })

  const upload = useMutation({
    mutationFn: async (file: File) => {
      const fd = new FormData()
      fd.append('file', file)
      const res = await api.post<ImportJobItem>('/imports', fd)
      return res.data
    },
    onSuccess: (data) => { setJobId(data.id); toast.push('success', `Parsed ${data.totalRows} rows`, 'Review the suggested column mapping.') },
    onError: (e) => toast.push('error', 'Upload failed', apiError(e).message),
  })

  const [localMapping, setLocalMapping] = useState<Record<string, string>>({})
  useEffect(() => {
    if (job?.suggestedMapping) setLocalMapping(job.suggestedMapping as Record<string, string>)
  }, [job?.id, job?.suggestedMapping])
  const mapping = job?.mapping ?? localMapping

  const submit = useMutation({
    mutationFn: async (strategy: string) =>
      api.put(`/imports/${jobId}/mapping`, { mapping: localMapping, duplicateStrategy: strategy, options: {} }),
    onSuccess: () => { toast.push('success', 'Import started', 'Rows are being processed in the background.'); refetchJob() },
    onError: (e) => toast.push('error', 'Could not start import', apiError(e).message),
  })

  const { data: errorRows } = useQuery({
    queryKey: ['import-rows', jobId, 'INVALID'],
    queryFn: async () => (await api.get<PageResponse<ImportRowItem>>(`/imports/${jobId}/rows`, { params: { status: 'INVALID', size: 10 } })).data,
    enabled: open && !!jobId && (job?.status === 'COMPLETED' || job?.status === 'PARTIAL'),
  })

  const downloadErrors = async () => {
    const res = await api.get(`/imports/${jobId}/errors.csv`, { responseType: 'blob' })
    const url = URL.createObjectURL(res.data as Blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `import-errors-${jobId?.slice(0, 8)}.csv`
    a.click()
    URL.revokeObjectURL(url)
  }

  const close = () => {
    setJobId(null)
    qc.invalidateQueries({ queryKey: ['leads'] })
    qc.invalidateQueries({ queryKey: ['import-history'] })
    onClose()
  }

  const processing = job?.status === 'PROCESSING' || submit.isPending

  return (
    <Dialog open={open} onClose={close} title="Import leads" description="Excel (.xlsx) or CSV — map columns, review, then run. Duplicates are detected on email, phone, website and LinkedIn." wide>
      {/* History toggle */}
      <div className="mb-4 flex gap-2">
        <Button size="sm" variant={tab === 'wizard' ? 'primary' : 'secondary'} onClick={() => setTab('wizard')}>Import</Button>
        <Button size="sm" variant={tab === 'history' ? 'primary' : 'secondary'} onClick={() => setTab('history')}><History className="h-3.5 w-3.5" /> History</Button>
      </div>

      {tab === 'history' ? <ImportHistory /> : !jobId ? (
        <div
          onDragOver={(e) => e.preventDefault()}
          onDrop={(e) => { e.preventDefault(); const f = e.dataTransfer.files?.[0]; if (f) upload.mutate(f) }}
          className="flex flex-col items-center justify-center rounded-xl border-2 border-dashed border-slate-300 px-6 py-12 text-center hover:border-blue-400"
        >
          <FileSpreadsheet className="mb-3 h-10 w-10 text-slate-300" />
          <p className="text-sm font-medium text-slate-700">Drop your file here, or</p>
          <Button className="mt-2" variant="secondary" loading={upload.isPending} onClick={() => fileRef.current?.click()}>Choose file</Button>
          <input ref={fileRef} type="file" accept=".csv,.xlsx,.xls" hidden onChange={(e) => { const f = e.target.files?.[0]; if (f) upload.mutate(f) }} />
          <p className="mt-3 text-xs text-slate-400">CSV or Excel, up to 10 MB. The first sheet / row is treated as headers.</p>
        </div>
      ) : (
        <div className="space-y-4">
          <div className="flex items-center justify-between rounded-lg bg-slate-50 px-4 py-3">
            <div>
              <p className="text-sm font-semibold text-slate-800">{job?.fileName}</p>
              <p className="text-xs text-slate-500">{job?.totalRows.toLocaleString()} rows parsed · {job?.status}</p>
            </div>
            {processing && <Loader2 className="h-5 w-5 animate-spin text-blue-500" />}
          </div>

          {(job?.status === 'AWAITING_MAPPING' || job?.status === 'PROCESSING') && (
            <>
              <div>
                <p className="mb-2 text-sm font-semibold text-slate-700">1 · Column mapping</p>
                <div className="max-h-64 space-y-1.5 overflow-y-auto rounded-lg border border-slate-200 p-3">
                  {(job?.suggestedHeaders ?? []).map((h) => (
                    <div key={h} className="grid grid-cols-2 items-center gap-2">
                      <span className="truncate text-sm text-slate-600">{h}</span>
                      <Select
                        value={(mapping as Record<string, string>)[h] ?? ''}
                        onChange={(e) => setLocalMapping((m) => ({ ...m, [h]: e.target.value }))}
                      >
                        <option value="">— ignore —</option>
                        {(targets ?? []).map((t) => <option key={t.key} value={t.key}>{t.label}{t.required ? ' *' : ''}</option>)}
                      </Select>
                    </div>
                  ))}
                  <p className="pt-1 text-[11px] text-slate-400">* business_name is required. Values are normalized (emails lowercased, phones reduced to last 10 digits) for duplicate detection.</p>
                </div>
              </div>

              <div>
                <p className="mb-2 text-sm font-semibold text-slate-700">2 · Duplicate strategy</p>
                <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
                  {[
                    { id: 'SKIP', title: 'Skip duplicates', desc: 'Keep existing leads untouched.' },
                    { id: 'UPDATE_EXISTING', title: 'Update existing', desc: 'Overwrite fields on matched leads.' },
                    { id: 'CREATE_ANYWAY', title: 'Import anyway', desc: 'Create even when a match exists.' },
                  ].map((s) => (
                    <button
                      key={s.id}
                      onClick={() => submit.mutate(s.id)}
                      disabled={processing}
                      className="rounded-xl border border-slate-200 p-3 text-left transition-colors hover:border-blue-400 hover:bg-blue-50/50 disabled:opacity-50"
                    >
                      <p className="text-sm font-semibold text-slate-800">{s.title}</p>
                      <p className="mt-0.5 text-xs text-slate-500">{s.desc}</p>
                    </button>
                  ))}
                </div>
              </div>
            </>
          )}

          {(job?.status === 'COMPLETED' || job?.status === 'PARTIAL' || job?.status === 'FAILED') && (
            <div>
              <p className="mb-2 text-sm font-semibold text-slate-700">3 · Summary</p>
              <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
                <Stat label="Imported" value={job?.importedRows ?? 0} tone="text-emerald-600" />
                <Stat label="Duplicates" value={job?.duplicateRows ?? 0} />
                <Stat label="Invalid" value={job?.invalidRows ?? 0} tone="text-amber-600" />
                <Stat label="Total" value={job?.totalRows ?? 0} />
              </div>
              {errorRows && errorRows.content.length > 0 && (
                <div className="mt-3">
                  <div className="mb-1.5 flex items-center justify-between">
                    <p className="text-xs font-semibold uppercase text-slate-500">First problems</p>
                    <Button size="sm" variant="secondary" onClick={downloadErrors}><Download className="h-3.5 w-3.5" /> Error report</Button>
                  </div>
                  <div className="overflow-hidden rounded-lg border border-slate-200">
                    <Table>
                      <THead><TR><TH>Row</TH><TH>Issues</TH></TR></THead>
                      <tbody>
                        {errorRows.content.slice(0, 5).map((r) => (
                          <TR key={r.id}>
                            <TD>#{r.rowNumber}</TD>
                            <TD className="text-xs text-red-600">{Object.entries(r.errors ?? {}).map(([k, v]) => `${k}: ${v}`).join(' · ')}</TD>
                          </TR>
                        ))}
                      </tbody>
                    </Table>
                  </div>
                </div>
              )}
              <div className="mt-4 flex justify-end gap-2">
                <Button variant="secondary" onClick={() => setJobId(null)}>Import another file</Button>
                <Button onClick={close}><CheckCircle2 className="h-4 w-4" /> Done</Button>
              </div>
            </div>
          )}
        </div>
      )}
    </Dialog>
  )
}

function Stat({ label, value, tone }: { label: string; value: number; tone?: string }) {
  return (
    <div className="rounded-lg border border-slate-200 px-3 py-2.5">
      <p className="text-xs text-slate-500">{label}</p>
      <p className={`text-xl font-bold tabular-nums ${tone ?? 'text-slate-800'}`}>{value.toLocaleString()}</p>
    </div>
  )
}

function ImportHistory() {
  const { data } = useQuery({
    queryKey: ['import-history'],
    queryFn: async () => (await api.get<PageResponse<ImportJobItem>>('/imports', { params: { size: 10, sort: 'createdAt,desc' } })).data,
  })
  if (!data || data.content.length === 0) return <EmptyState icon={<Upload className="h-6 w-6" />} title="No imports yet" subtitle="Your import history will appear here with counts and error reports." />
  return (
    <div className="overflow-hidden rounded-lg border border-slate-200">
      <Table>
        <THead><TR><TH>File</TH><TH>Status</TH><TH>Rows</TH><TH>Imported</TH><TH>Duplicates</TH><TH>Invalid</TH><TH>When</TH></TR></THead>
        <tbody>
          {data.content.map((j) => (
            <TR key={j.id}>
              <TD className="font-medium text-slate-700">{j.fileName}</TD>
              <TD><Badge tone={j.status === 'COMPLETED' ? 'green' : j.status === 'FAILED' ? 'red' : 'yellow'}>{j.status}</Badge></TD>
              <TD>{j.totalRows}</TD>
              <TD className="text-emerald-600">{j.importedRows}</TD>
              <TD>{j.duplicateRows}</TD>
              <TD className="text-amber-600">{j.invalidRows}</TD>
              <TD className="text-slate-500">{fmtDateTime(j.createdAt)}</TD>
            </TR>
          ))}
        </tbody>
      </Table>
    </div>
  )
}
