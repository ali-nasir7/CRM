import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { Mail } from 'lucide-react'
import { api } from '@/api/client'
import type { EmailItem, PageResponse } from '@/types'
import { PageHeader } from '@/components/shared/PageHeader'
import { DataTable, type Column } from '@/components/shared/DataTable'
import { Badge } from '@/components/ui/Badge'
import { Select } from '@/components/ui/Input'
import { fmtDateTime } from '@/lib/utils'

const STATUS_TONES: Record<string, 'gray' | 'blue' | 'green' | 'red'> = {
  QUEUED: 'gray', SENT: 'blue', OPENED: 'green', REPLIED: 'green', BOUNCED: 'red', FAILED: 'red', SUPPRESSED: 'gray',
}

export default function EmailsPage() {
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const [direction, setDirection] = useState('')
  const [status, setStatus] = useState('')

  const { data, isFetching } = useQuery({
    queryKey: ['emails', page, direction, status],
    queryFn: async () => (await api.get<PageResponse<EmailItem>>('/emails', { params: { page, size: 25, direction: direction || undefined, status: status || undefined, sort: 'createdAt,desc' } })).data,
  })

  const columns: Column<EmailItem>[] = [
    { key: 'subject', header: 'Subject', render: (m) => <span className="font-medium text-slate-800">{m.subject ?? '(no subject)'}</span> },
    { key: 'to', header: 'To', render: (m) => <span className="text-slate-600">{m.toEmails?.join(', ')}</span> },
    { key: 'from', header: 'From', render: (m) => <span className="text-slate-500">{m.fromEmail}</span> },
    { key: 'direction', header: 'Direction', render: (m) => <Badge tone={m.direction === 'OUTBOUND' ? 'blue' : 'purple'}>{m.direction}</Badge> },
    {
      key: 'status', header: 'Status', render: (m) => (
        <div className="flex gap-1">
          <Badge tone={STATUS_TONES[m.status] ?? 'gray'}>{m.status}</Badge>
          {m.openedAt && <Badge tone="green">👁 {m.openCount}</Badge>}
          {m.repliedAt && <Badge tone="green">↩ replied</Badge>}
          {m.bouncedAt && <Badge tone="red">bounce</Badge>}
        </div>
      ),
    },
    { key: 'campaign', header: 'Campaign', render: (m) => (m.campaignId ? <Badge tone="purple">Campaign</Badge> : <span className="text-slate-400">—</span>) },
    { key: 'sent', header: 'Sent', render: (m) => <span className="text-slate-500">{fmtDateTime(m.sentAt ?? m.createdAt)}</span> },
  ]

  return (
    <div>
      <PageHeader
        title="Email activity"
        subtitle="All emails sent through connected accounts, with open / reply / bounce tracking."
        actions={
          <div className="flex flex-wrap gap-2">
            <Link to="/emails/accounts" className="inline-flex items-center rounded-lg bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700">Accounts</Link>
            <Link to="/emails/templates" className="inline-flex items-center rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50">Templates</Link>
            <Link to="/emails/suppressions" className="inline-flex items-center rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50">Suppressions</Link>
          </div>
        }
      />
      <div className="card mb-3 flex flex-wrap gap-2 p-3">
        <Select value={direction} onChange={(e) => { setDirection(e.target.value); setPage(0) }} className="w-40">
          <option value="">All directions</option>
          <option value="OUTBOUND">Outbound</option>
          <option value="INBOUND">Inbound</option>
        </Select>
        <Select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0) }} className="w-40">
          <option value="">All statuses</option>
          {Object.keys(STATUS_TONES).map((s) => <option key={s}>{s}</option>)}
        </Select>
      </div>
      <div className="card">
        <DataTable data={data} loading={isFetching} columns={columns} onPageChange={setPage}
          onRowClick={(m) => m.leadId && navigate(`/leads/${m.leadId}`)}
          empty={{ icon: <Mail className="h-6 w-6" />, title: 'No emails yet', subtitle: 'Send your first email from a lead page, or run a campaign.' } }/>
      </div>
    </div>
  )
}
