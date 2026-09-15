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
  operator: string
  reason: string
}

export interface TeamStatistics {
  teamId: number
  teamName: string
  equipmentCount: number
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
  totalEquipment: number
  totalTeam: number
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
  getUnassigned: () => request.get<Equipment[]>('/assignment/unassigned')
}

export const statisticsApi = {
  getOverview: () => request.get<{ totalEquipment: number; totalTeam: number; averageEquipmentPerTeam: number }>('/statistics/overview'),
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