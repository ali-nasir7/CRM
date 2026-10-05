export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface UserInfo {
  id: string
  email: string
  firstName: string
  lastName: string
  displayName: string
  superAdmin: boolean
  roles: string[]
  permissions: string[]
  dailyTargets: Record<string, number> | null
  organizationName: string | null
  organizationId?: string
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  expiresInSeconds: number
  user: UserInfo
}

export interface LeadItem {
  id: string
  businessName: string
  firstName: string | null
  lastName: string | null
  contactName: string | null
  jobTitle: string | null
  email: string | null
  phone: string | null
  whatsapp: string | null
  website: string | null
  linkedin: string | null
  country: string | null
  state: string | null
  city: string | null
  address: string | null
  timezone: string | null
  industry: string | null
  businessType: string | null
  companySize: string | null
  employeesCount: number | null
  revenueRange: string | null
  customFields: Record<string, unknown> | null
  status: string
  score: number
  scoreCategory: string
  sourceId: string | null
  sourceName: string | null
  pipelineId: string | null
  stageId: string | null
  stageName: string | null
  assignedUserId: string | null
  assignedUserName: string | null
  lastContactedAt: string | null
  nextFollowUpAt: string | null
  tags: string[]
  notes: string | null
  companyId: string | null
  contactId: string | null
  createdAt: string
  updatedAt: string
}

export interface LeadFilters {
  q?: string
  status?: string
  stageId?: string
  assignedTo?: string
  sourceId?: string
  country?: string
  city?: string
  industry?: string
  tags?: string[]
  minScore?: number
  uncontacted?: boolean
  createdFrom?: string
  createdTo?: string
  sort?: string
  page?: number
  size?: number
}

export interface UserItem {
  id: string
  email: string
  firstName: string
  lastName: string
  displayName: string
  jobTitle: string | null
  phone: string | null
  status: string
  superAdmin: boolean
  roleKeys: string[]
  teams: { id: string; name: string }[]
  dailyTargets: Record<string, number> | null
  lastLoginAt: string | null
  tempPassword?: string | null
  createdAt: string
}

/** Org-directory entry from GET /users/lite (assign/chat pickers; works for every role). */
export interface LiteUserItem {
  id: string
  displayName: string
  email: string
}

export interface RoleItem {
  id: string
  key: string
  name: string
  description: string | null
  dataScope: string
  system: boolean
  permissionKeys: string[]
  userCount: number
}

export interface TeamItem {
  id: string
  name: string
  description: string | null
  managerId: string | null
  managerName: string | null
  members: { id: string; displayName: string; email: string }[]
  createdAt: string
}

export interface PipelineItem {
  id: string
  name: string
  description: string | null
  isDefault: boolean
  stages: StageItem[]
}

export interface StageItem {
  id: string
  pipelineId: string
  name: string
  position: number
  type: string
  probability: number
}

export interface ActivityItem {
  id: string
  type: string
  leadId: string | null
  actorId: string | null
  actorName: string | null
  subject: string | null
  body: string | null
  metadata: Record<string, unknown> | null
  occurredAt: string
  createdAt: string
}

export interface TaskItem {
  id: string
  title: string
  description: string | null
  leadId: string | null
  businessName: string | null
  taskType: string
  assignedUserId: string
  assignedUserName: string | null
  createdBy: string | null
  dueAt: string
  priority: string
  status: string
  completedAt: string | null
  completionNote: string | null
  createdAt: string
}

export interface CallItem {
  id: string
  leadId: string | null
  businessName: string | null
  userId: string
  userName: string | null
  direction: string
  occurredAt: string
  durationSeconds: number | null
  outcome: string
  notes: string | null
  nextAction: string | null
  followUpAt: string | null
  createdAt: string
}

export interface MeetingItem {
  id: string
  title: string
  leadId: string | null
  businessName: string | null
  companyId: string | null
  ownerId: string
  ownerName: string | null
  participants: string[] | null
  startAt: string
  durationMinutes: number
  meetingLink: string | null
  location: string | null
  notes: string | null
  status: string
  createdAt: string
}

export interface DealItem {
  id: string
  title: string
  leadId: string | null
  businessName: string | null
  companyId: string | null
  companyName: string | null
  contactId: string | null
  ownerId: string
  ownerName: string | null
  pipelineId: string | null
  stageId: string | null
  stageName: string | null
  amount: string | null
  currency: string
  probability: number
  expectedCloseDate: string | null
  closedAt: string | null
  status: string
  lostReason: string | null
  products: string[] | null
  notes: string | null
  clientId: string | null
  createdAt: string
}

export interface DealSummary {
  openValue: string
  weightedValue: string
  wonRevenue: string
  lostRevenue: string
  openCount: number
  wonCount: number
  lostCount: number
  expectedRevenue: string
}

export interface CompanyItem {
  id: string
  name: string
  website: string | null
  industry: string | null
  description: string | null
  phone: string | null
  email: string | null
  country: string | null
  city: string | null
  state: string | null
  address: string | null
  linkedin: string | null
  companySize: string | null
  annualRevenue: string | null
  ownerId: string | null
  ownerName: string | null
  tags: string[]
  createdAt: string
  updatedAt: string
}

export interface ContactItem {
  id: string
  companyId: string | null
  companyName: string | null
  firstName: string
  lastName: string
  displayName: string
  jobTitle: string | null
  email: string | null
  secondaryEmail: string | null
  phone: string | null
  whatsapp: string | null
  linkedin: string | null
  ownerId: string | null
  ownerName: string | null
  primary: boolean
  notes: string | null
  createdAt: string
}

export interface CampaignItem {
  id: string
  name: string
  description: string | null
  accountId: string | null
  accountEmail: string | null
  status: string
  scheduledAt: string | null
  totalRecipients: number
  sentCount: number
  openCount: number
  replyCount: number
  bounceCount: number
  unsubscribeCount: number
  steps: { id: string; position: number; templateId: string; delayDays: number }[]
  createdAt: string
}

export interface TemplateItem {
  id: string
  name: string
  subject: string
  bodyHtml: string | null
  bodyText: string | null
  category: string | null
  active: boolean
  variables: string[]
  createdAt: string
}

export interface AccountItem {
  id: string
  provider: string
  email: string
  displayName: string | null
  replyTo: string | null
  smtpHost: string | null
  smtpPort: number | null
  smtpEncryption: string | null
  status: string
  verifiedAt: string | null
  dailyLimit: number
  userId: string
  createdAt: string
}

export interface EmailItem {
  id: string
  leadId: string | null
  accountId: string | null
  fromEmail: string | null
  toEmails: string[]
  subject: string | null
  direction: string
  status: string
  sentAt: string | null
  openedAt: string | null
  openCount: number | null
  repliedAt: string | null
  bouncedAt: string | null
  campaignId: string | null
  preview: string | null
  createdAt: string | null
}

export interface ProposalItem {
  id: string
  proposalNumber: string
  title: string
  description: string | null
  status: string
  leadId: string | null
  businessName: string | null
  dealId: string | null
  companyId: string | null
  companyName: string | null
  contactId: string | null
  contactName: string | null
  currency: string
  subtotal: string
  discountPercent: string | null
  discountAmount: string
  taxPercent: string | null
  taxAmount: string
  total: string
  validUntil: string | null
  terms: string | null
  sentAt: string | null
  viewedAt: string | null
  decidedAt: string | null
  items: { id: string; name: string; description: string | null; quantity: string; unitPrice: string; total: string }[]
  createdAt: string
}

export interface ClientItem {
  id: string
  companyId: string
  companyName: string | null
  website: string | null
  primaryContactId: string | null
  primaryContactName: string | null
  accountManagerId: string | null
  accountManagerName: string | null
  status: string
  lifetimeValue: string | null
  convertedFromLeadId: string | null
  convertedAt: string
  notes: string | null
  createdAt: string
}

export interface ImportJobItem {
  id: string
  fileName: string
  fileType: string | null
  totalRows: number
  validRows: number
  duplicateRows: number
  invalidRows: number
  importedRows: number
  status: string
  mapping: Record<string, string> | null
  suggestedHeaders: string[] | null
  suggestedMapping: Record<string, string> | null
  duplicateStrategy: string
  options: Record<string, unknown> | null
  errorMessage: string | null
  createdAt: string
  completedAt: string | null
  createdByEmail: string | null
}

export interface ImportRowItem {
  id: string
  rowNumber: number
  raw: Record<string, unknown> | null
  status: string
  errors: Record<string, unknown> | null
  duplicateOfLeadId: string | null
  importedLeadId: string | null
}

export interface SavedViewItem {
  id: string
  name: string
  shared: boolean
  mine: boolean
  filters: Record<string, unknown>
  sort: string | null
}

export interface AuditItem {
  id: string
  action: string
  entityType: string
  entityId: string
  entityLabel: string
  actorEmail: string
  oldValues: Record<string, unknown> | null
  newValues: Record<string, unknown> | null
  ip: string
  createdAt: string
  /** Resolved human names for ids inside old/new values (stage/user/client...), from the backend. */
  labels?: Record<string, string>
}

export interface NotificationItem {
  id: string
  type: string
  title: string
  body: string | null
  entityType: string | null
  entityId: string | null
  readAt: string | null
  createdAt: string
}

export interface DocumentItem {
  id: string
  name: string
  fileName: string
  contentType: string | null
  sizeBytes: number
  leadId: string | null
  companyId: string | null
  dealId: string | null
  proposalId: string | null
  clientId: string | null
  createdAt: string
}

export interface ScoringRuleItem {
  id: string
  criterion: string
  operand: string | null
  points: number
  label: string
  active: boolean
  position: number
}

export interface AutomationItem {
  id: string
  name: string
  trigger: string
  conditions: Record<string, unknown> | null
  action: string
  actionConfig: Record<string, unknown> | null
  active: boolean
  runCount: number
}

export interface TagItem { id: string; name: string; color: string | null }
export interface SourceItem { id: string; key: string; name: string; description: string | null }
export interface CustomFieldItem { id: string; key: string; label: string; type: string; options: string[] | null; position: number }

// ---- Calling (user-specific cellular bridge + CRM Phone Agent) ----
export interface CallingDeviceItem {
  id: string
  deviceName: string
  phoneNumber: string | null
  platform: string | null
  status: string
  isDefault: boolean
  lastSeenAt: string | null
  connectionMode: 'AGENT' | 'BRIDGE'
  appVersion: string | null
  deviceModel: string | null
  revokedAt: string | null
  userId: string
}

export interface PairingStartResponse { code: string; expiresAt: string }

/** Response of GET /calling/health - powers the Call Health panel. */
export interface BridgeHealthInfo {
  configured: boolean
  reachable: boolean
  latencyMs?: number | null
  version?: string | null
  phonePresent?: boolean | null
  adbState?: string | null
  simState?: string | null
  activeCalls?: number | null
  error?: string | null
}
export interface CallingHealthDevice {
  id: string
  deviceName: string
  platform: string | null
  status: string
  lastSeenAt: string | null
  isDefault: boolean
  bridgeUrlSet: boolean
}
export interface CallingHealth {
  mode: 'GLOBAL_BRIDGE' | 'DEVICE_BRIDGE' | 'NOT_CONFIGURED'
  bridge: BridgeHealthInfo
  devices: CallingHealthDevice[]
}

export interface CallStateItem {
  id: string
  status: string
  outcome: string | null
  number: string | null
  deviceId: string | null
  startedAt: string | null
  answeredAt: string | null
  endedAt: string | null
  durationSeconds: number | null
}

// ---- Internal team chat ----
export interface ChatConversationItem {
  id: string
  participantIds: string[]
  participantNames: string[]
  lastMessage: string | null
  lastMessageAt: string | null
  unreadCount: number
}

export interface ChatMessageItem {
  id: string
  senderId: string
  senderName: string
  body: string
  leadId: string | null
  createdAt: string
}
