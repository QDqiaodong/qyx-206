import axios from 'axios'

const request = axios.create({
  baseURL: '/api',
  timeout: 5000
})

request.interceptors.response.use(
  (response) => {
    return response.data
  },
  (error) => {
    console.error('Request error:', error)
    return Promise.reject(error)
  }
)

export interface Equipment {
  id?: number
  equipmentCode: string
  trainingPurpose: string
  sizeSpec: string
  createTime?: string
  updateTime?: string
}

export interface Team {
  id?: number
  teamName: string
  memberCount: number
  description: string
  createTime?: string
  updateTime?: string
}

export interface Assignment {
  id?: number
  equipmentId: number
  teamId: number
  bindTime?: string
  operator: string
  remark: string
}

export interface AssignmentHistory {
  id?: number
  equipmentId: number
  equipmentCode?: string
  oldTeamId?: number
  oldTeamName?: string
  newTeamId: number
  newTeamName?: string
  changeTime?: string
  /** 这一段归属从什么时候开始 */
  validFrom?: string
  /** 这一段归属到什么时候结束；空 = 至今仍在用 */
  validTo?: string
  operator: string
  reason: string
}

export interface AssignmentInterval {
  historyId: number
  equipmentId: number
  equipmentCode: string
  teamId: number
  teamName: string
  validFrom: string
  validTo?: string
  /** 是否当前仍在用的最后一段 */
  current: boolean
  /** 这一段待了多久，如 "3天4小时" */
  durationText?: string
  operator?: string
  reason?: string
}

export interface OwnershipMismatch {
  /** TEAM_MISMATCH=归属单与末段班组不一致；NO_INTERVAL=无归属区间；MULTIPLE_OPEN=多段在用区间；ORPHAN_INTERVAL=有区间无归属单 */
  type: 'TEAM_MISMATCH' | 'NO_INTERVAL' | 'MULTIPLE_OPEN' | 'ORPHAN_INTERVAL'
  equipmentId: number
  equipmentCode: string
  assignmentTeamId?: number
  assignmentTeamName?: string
  segmentTeamId?: number
  segmentTeamName?: string
  detail: string
}

export interface IntervalMigrationResult {
  legacyEquipmentCount: number
  migratedEquipmentCount: number
  migratedSegmentCount: number
  problems: { equipmentId: number; equipmentCode: string; reason: string }[]
}

export interface TeamStatistics {
  teamId: number
  teamName: string
  /** 可训器材：名下在库、无未结束占用、无在外未还的件数 */
  equipmentCount: number
  /** 名下归属器材总件数（含被占用/在外件，仅作对照） */
  assignedCount: number
}

export interface TrainingOccupancy {
  id?: number
  equipmentId: number
  equipmentCode?: string
  trainingPurpose?: string
  sizeSpec?: string
  teamId: number
  teamName?: string
  trainingDate: string
  startTime: string
  endTime: string
  courseName: string
  operator?: string
  status?: 'ACTIVE' | 'CANCELLED'
  createTime?: string
  cancelTime?: string
  cancelOperator?: string
  cancelReason?: string
}

export interface OverviewStatistics {
  /** 在库可训器材数：没有未结束占用、也没有在外未还 */
  totalEquipment: number
  /** 库里登记总件数（对照用，不参与均数） */
  registeredEquipment: number
  /** 被未结束占用/在外未还占住、暂不可训的件数 */
  unavailableEquipment: number
  totalTeam: number
  /** 班均可训器材 = 在库可训器材 / 班组数 */
  averageEquipmentPerTeam: number
}

export interface EquipmentLoan {
  id?: number
  equipmentId: number
  equipmentCode?: string
  trainingPurpose?: string
  sizeSpec?: string
  teamId: number
  teamName?: string
  checkoutTime?: string
  expectedReturnTime: string
  companions: string
  reason: string
  operator?: string
  /** 落库状态：OUT / OVERDUE / RETURNED */
  status?: 'OUT' | 'OVERDUE' | 'RETURNED'
  /** 读时实际状态：扫描未跑到时，OUT 已过点也会呈现为 OVERDUE */
  effectiveStatus?: 'OUT' | 'OVERDUE' | 'RETURNED'
  overdue?: boolean
  overdueTime?: string
  returnTime?: string
  returnOperator?: string
  returnRemark?: string
}

export interface LoanSummary {
  openCount: number
  overdueCount: number
  returnedCount: number
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export const equipmentApi = {
  getList: (params: { page: number; size: number; keyword?: string }) =>
    request.get<PageResponse<Equipment>>('/equipment', { params }),
  getById: (id: number) => request.get<Equipment>(`/equipment/${id}`),
  create: (data: Equipment) => request.post('/equipment', data),
  update: (id: number, data: Equipment) => request.put(`/equipment/${id}`, data),
  delete: (id: number) => request.delete(`/equipment/${id}`)
}

export const teamApi = {
  getList: (params: { page: number; size: number; keyword?: string }) =>
    request.get<PageResponse<Team>>('/team', { params }),
  getById: (id: number) => request.get<Team>(`/team/${id}`),
  create: (data: Team) => request.post('/team', data),
  update: (id: number, data: Team) => request.put(`/team/${id}`, data),
  delete: (id: number) => request.delete(`/team/${id}`),
  getAll: () => request.get<Team[]>('/team/all')
}

export const assignmentApi = {
  bind: (data: Assignment) => request.post('/assignment/bind', data),
  adjust: (data: { equipmentId: number; newTeamId: number; operator: string; reason: string }) =>
    request.put('/assignment/adjust', data),
  getHistory: (params: { page: number; size: number }) =>
    request.get<PageResponse<AssignmentHistory>>('/assignment/history', { params }),
  getByTeam: (teamId: number) => request.get<Equipment[]>(`/assignment/team/${teamId}`),
  getUnassigned: () => request.get<Equipment[]>('/assignment/unassigned'),
  getIntervals: (equipmentId: number) =>
    request.get<AssignmentInterval[]>(`/assignment/equipment/${equipmentId}/intervals`),
  getMismatches: () => request.get<OwnershipMismatch[]>('/assignment/ownership-mismatches'),
  migrateIntervals: () => request.post<IntervalMigrationResult>('/assignment/history/migrate-intervals')
}

export const statisticsApi = {
  getOverview: () => request.get<OverviewStatistics>('/statistics/overview'),
  getTeamStatistics: () => request.get<TeamStatistics[]>('/statistics/team')
}

export interface OccupancyPageParams {
  trainingDate: string
  teamId?: number
  equipmentId?: number
  includeCancelled?: boolean
  page?: number
  size?: number
}

export const occupancyApi = {
  create: (data: {
    equipmentId: number
    teamId: number
    trainingDate: string
    startTime: string
    endTime: string
    courseName: string
    operator: string
  }) => request.post<TrainingOccupancy>('/occupancy', data),
  list: (params: OccupancyPageParams) =>
    request.get<PageResponse<TrainingOccupancy>>('/occupancy', { params }),
  getByEquipmentDay: (equipmentId: number, trainingDate: string) =>
    request.get<TrainingOccupancy[]>(`/occupancy/equipment/${equipmentId}`, { params: { trainingDate } }),
  teamActiveCount: (teamId: number, trainingDate: string) =>
    request.get<{ teamId: number; trainingDate: string; activeCount: number }>(
      `/occupancy/team/${teamId}/active-count`,
      { params: { trainingDate } }
    ),
  activeCounts: (trainingDate: string) =>
    request.get<{ teamId: number; activeCount: number }[]>('/occupancy/active-counts', {
      params: { trainingDate }
    })
}

export interface LoanPageParams {
  tab?: 'OPEN' | 'OVERDUE' | 'RETURNED' | 'ALL'
  teamId?: number
  equipmentId?: number
  page?: number
  size?: number
}

export const loanApi = {
  checkout: (data: {
    equipmentId: number
    teamId: number
    expectedReturnTime: string
    companions: string
    reason: string
    operator?: string
  }) => request.post<EquipmentLoan>('/loan/checkout', data),
  returnLoan: (id: number, data?: { operator?: string; remark?: string }) =>
    request.put<EquipmentLoan>(`/loan/${id}/return`, data ?? {}),
  list: (params: LoanPageParams) => request.get<PageResponse<EquipmentLoan>>('/loan', { params }),
  summary: () => request.get<LoanSummary>('/loan/summary'),
  sweepOverdue: () => request.post<{ markedOverdue: number }>('/loan/sweep-overdue')
}