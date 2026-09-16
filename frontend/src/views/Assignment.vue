<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Link2, RefreshCw, Search, Wrench } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import {
  assignmentApi,
  equipmentApi,
  teamApi,
  type AssignmentInterval,
  type Equipment,
  type IntervalMigrationResult,
  type OwnershipMismatch,
  type Team
} from '@/api'

const unassignedEquipments = ref<Equipment[]>([])
const teams = ref<Team[]>([])
const historyData = ref<any[]>([])
const historyTotal = ref(0)
const historyPage = ref(1)
const historyPageSize = ref(10)
const activeTab = ref('bind')

const bindDialogVisible = ref(false)

const bindForm = ref({
  equipmentId: 0,
  teamId: 0,
  operator: '管理员',
  remark: ''
})

// 归属调整
const assignedData = ref<any[]>([])
const adjustDialogVisible = ref(false)
const adjustForm = ref({
  equipmentId: 0,
  equipmentCode: '',
  currentTeamId: 0,
  newTeamId: 0,
  operator: '管理员',
  reason: ''
})

// 归属区间：一件器材从哪天到哪天归哪个班组、待了多久
const allEquipments = ref<Equipment[]>([])
const intervalEquipmentId = ref<number | null>(null)
const intervals = ref<AssignmentInterval[]>([])
const intervalsLoading = ref(false)

// 对账与老流水迁移
const mismatches = ref<OwnershipMismatch[]>([])
const migrationResult = ref<IntervalMigrationResult | null>(null)
const migrating = ref(false)

const mismatchTypeLabel = (type: OwnershipMismatch['type']) => {
  switch (type) {
    case 'TEAM_MISMATCH': return '归属与末段不一致'
    case 'NO_INTERVAL': return '无归属区间'
    case 'MULTIPLE_OPEN': return '多段在用区间'
    case 'ORPHAN_INTERVAL': return '有区间无归属单'
    default: return type
  }
}

// 班组下拉选项与班组档案同版：名称后跟在编制与职责说明，
// 派活时照选项上的最新编制走，不再各页各记
const teamOptionLabel = (t: Team) => {
  const parts = [`${t.teamName}（编制 ${t.memberCount ?? 0} 人`]
  if (t.description && t.description.trim()) parts[0] += ` · ${t.description.trim()}`
  parts[0] += '）'
  return parts[0]
}

const fetchData = async () => {
  unassignedEquipments.value = await assignmentApi.getUnassigned() as any
  teams.value = await teamApi.getAll() as any
}

const fetchAssigned = async () => {
  // 逐班组汇总当前归属关系
  const rows: any[] = []
  for (const team of teams.value) {
    const eqs = (await assignmentApi.getByTeam(team.id!)) as any as Equipment[]
    for (const eq of eqs) {
      rows.push({ ...eq, teamId: team.id, teamName: team.teamName })
    }
  }
  assignedData.value = rows
}

const fetchHistory = async () => {
  const params = {
    page: historyPage.value - 1,
    size: historyPageSize.value
  }
  const result: any = await assignmentApi.getHistory(params)
  historyData.value = result.content
  historyTotal.value = result.totalElements
}

const fetchAllEquipments = async () => {
  const result: any = await equipmentApi.getList({ page: 0, size: 500 })
  allEquipments.value = result.content
}

const fetchIntervals = async () => {
  if (!intervalEquipmentId.value) {
    intervals.value = []
    return
  }
  intervalsLoading.value = true
  try {
    intervals.value = await assignmentApi.getIntervals(intervalEquipmentId.value) as any
  } finally {
    intervalsLoading.value = false
  }
}

const fetchMismatches = async () => {
  mismatches.value = await assignmentApi.getMismatches() as any
}

const runMigration = async () => {
  migrating.value = true
  try {
    const r = await assignmentApi.migrateIntervals() as any as IntervalMigrationResult
    migrationResult.value = r
    if (r.problems.length > 0) {
      ElMessage.warning(`推区间完成：成功 ${r.migratedEquipmentCount} 件，${r.problems.length} 件推不动已单列`)
    } else {
      ElMessage.success(`推区间完成：${r.migratedEquipmentCount} 件、共 ${r.migratedSegmentCount} 段`)
    }
    fetchMismatches()
    fetchHistory()
  } finally {
    migrating.value = false
  }
}

const openBindDialog = (equipment: Equipment) => {
  bindForm.value = {
    equipmentId: equipment.id!,
    teamId: 0,
    operator: '管理员',
    remark: ''
  }
  bindDialogVisible.value = true
}

const handleBind = async () => {
  if (!bindForm.value.teamId) {
    ElMessage.warning('请选择班组')
    return
  }
  await assignmentApi.bind(bindForm.value)
  ElMessage.success('绑定成功')
  bindDialogVisible.value = false
  fetchData()
  fetchAssigned()
}

const openAdjustDialog = (row: any) => {
  adjustForm.value = {
    equipmentId: row.id,
    equipmentCode: row.equipmentCode,
    currentTeamId: row.teamId,
    newTeamId: 0,
    operator: '管理员',
    reason: ''
  }
  adjustDialogVisible.value = true
}

const handleAdjust = async () => {
  if (!adjustForm.value.newTeamId) {
    ElMessage.warning('请选择新班组')
    return
  }
  if (adjustForm.value.newTeamId === adjustForm.value.currentTeamId) {
    ElMessage.warning('新班组与原班组相同')
    return
  }
  await assignmentApi.adjust({
    equipmentId: adjustForm.value.equipmentId,
    newTeamId: adjustForm.value.newTeamId,
    operator: adjustForm.value.operator,
    reason: adjustForm.value.reason
  })
  ElMessage.success('归属已调整，该器材未结束的课目占用已当场作废')
  adjustDialogVisible.value = false
  fetchData()
  fetchAssigned()
}

const handleTabChange = (name: string) => {
  if (name === 'adjust') fetchAssigned()
  if (name === 'history') fetchHistory()
  if (name === 'intervals') {
    if (allEquipments.value.length === 0) fetchAllEquipments()
    fetchIntervals()
  }
  if (name === 'reconcile') fetchMismatches()
}

onMounted(() => {
  fetchData()
  fetchHistory()
})
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <h2 class="text-xl font-bold text-fire-dark">归属绑定管理</h2>
      <el-button @click="fetchData">
        <RefreshCw class="w-4 h-4 mr-2" />
        刷新
      </el-button>
    </div>

    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="待绑定器材" name="bind">
        <div class="bg-white p-4 rounded-lg shadow">
          <div v-if="unassignedEquipments.length === 0" class="text-center text-gray-400 py-8">
            暂无待绑定器材
          </div>
          <el-table v-else :data="unassignedEquipments">
            <el-table-column prop="equipmentCode" label="器材编号" width="150" />
            <el-table-column prop="trainingPurpose" label="训练用途" width="200" />
            <el-table-column prop="sizeSpec" label="尺寸规格" width="150" />
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <el-button type="primary" size="small" @click="openBindDialog(row)">
                  <Link2 class="w-4 h-4 mr-1" />
                  绑定班组
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="归属调整" name="adjust">
        <div class="bg-white p-4 rounded-lg shadow">
          <el-alert
            type="warning"
            :closable="false"
            show-icon
            title="调整归属不会被拦截；该器材尚未结束的课目占用会当场作废，并留下谁在何时因改班作废的痕迹。"
            class="mb-3"
          />
          <el-table :data="assignedData">
            <el-table-column prop="equipmentCode" label="器材编号" width="140" />
            <el-table-column prop="trainingPurpose" label="训练用途" width="180" />
            <el-table-column prop="sizeSpec" label="尺寸规格" width="150" />
            <el-table-column prop="teamName" label="当前班组" width="140" />
            <el-table-column label="操作" width="140">
              <template #default="{ row }">
                <el-button type="primary" size="small" @click="openAdjustDialog(row)">
                  <RefreshCw class="w-4 h-4 mr-1" />
                  调整归属
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="变更记录" name="history">
        <div class="bg-white p-4 rounded-lg shadow">
          <el-table :data="historyData">
            <el-table-column prop="equipmentCode" label="器材编号" width="140" />
            <el-table-column prop="oldTeamName" label="原班组" width="110" />
            <el-table-column prop="newTeamName" label="新班组" width="110" />
            <el-table-column prop="changeTime" label="变更时间" width="170" />
            <el-table-column label="区间开始" width="170">
              <template #default="{ row }">
                {{ row.validFrom ?? '—' }}
              </template>
            </el-table-column>
            <el-table-column label="区间结束" width="170">
              <template #default="{ row }">
                <span v-if="row.validTo">{{ row.validTo }}</span>
                <el-tag v-else-if="row.validFrom" type="success" size="small">至今</el-tag>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column prop="operator" label="操作人" width="90" />
            <el-table-column prop="reason" label="变更原因" min-width="160" />
          </el-table>
          <div class="flex justify-center mt-4">
            <el-pagination
              v-model:current-page="historyPage"
              v-model:page-size="historyPageSize"
              :total="historyTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="fetchHistory"
              @size-change="fetchHistory"
            />
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="归属区间" name="intervals">
        <div class="bg-white p-4 rounded-lg shadow">
          <div class="flex items-center gap-3 mb-4">
            <el-select
              v-model="intervalEquipmentId"
              placeholder="选择器材查看归属区间"
              filterable
              clearable
              class="w-72"
              @change="fetchIntervals"
            >
              <el-option
                v-for="eq in allEquipments"
                :key="eq.id"
                :label="`${eq.equipmentCode}（${eq.trainingPurpose}）`"
                :value="eq.id!"
              />
            </el-select>
            <el-button @click="fetchIntervals" :disabled="!intervalEquipmentId">
              <Search class="w-4 h-4 mr-1" />
              查询
            </el-button>
          </div>
          <el-table :data="intervals" v-loading="intervalsLoading">
            <el-table-column prop="teamName" label="班组" width="140" />
            <el-table-column prop="validFrom" label="从何时起" width="180" />
            <el-table-column label="到何时止" width="180">
              <template #default="{ row }">
                <span v-if="row.validTo">{{ row.validTo }}</span>
                <el-tag v-else-if="row.current" type="success" size="small">至今</el-tag>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column label="这一段待了多久" width="140">
              <template #default="{ row }">
                {{ row.durationText }}{{ row.current ? '（至今）' : '' }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag v-if="row.current" type="success" size="small">在用</el-tag>
                <el-tag v-else type="info" size="small">已交接</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="operator" label="操作人" width="100" />
            <el-table-column prop="reason" label="原因" min-width="160" />
          </el-table>
          <div v-if="intervalEquipmentId && intervals.length === 0 && !intervalsLoading"
               class="text-center text-gray-400 py-8">
            该器材还没有归属区间（老流水可能尚未推区间，请到「对账迁移」执行迁移）
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="对账迁移" name="reconcile">
        <div class="space-y-4">
          <div class="bg-white p-4 rounded-lg shadow">
            <div class="flex items-center justify-between mb-3">
              <div>
                <h3 class="font-bold text-fire-dark">老流水推区间</h3>
                <p class="text-sm text-gray-500 mt-1">
                  把没有区间的老变更记录按时间顺序一次性推成首尾相接的区间；时间戳重复、最早一段推不出起点的器材会逐件列出，不按猜的补。
                </p>
              </div>
              <el-button type="primary" :loading="migrating" @click="runMigration">
                <Wrench class="w-4 h-4 mr-1" />
                执行迁移
              </el-button>
            </div>
            <template v-if="migrationResult">
              <el-alert
                :type="migrationResult.problems.length > 0 ? 'warning' : 'success'"
                :closable="false"
                show-icon
                :title="`待迁移 ${migrationResult.legacyEquipmentCount} 件，成功 ${migrationResult.migratedEquipmentCount} 件（共 ${migrationResult.migratedSegmentCount} 段），推不动 ${migrationResult.problems.length} 件`"
                class="mb-3"
              />
              <el-table v-if="migrationResult.problems.length > 0" :data="migrationResult.problems" size="small">
                <el-table-column prop="equipmentId" label="器材ID" width="100" />
                <el-table-column prop="equipmentCode" label="器材编号" width="160" />
                <el-table-column prop="reason" label="推不动的原因" min-width="240" />
              </el-table>
            </template>
          </div>

          <div class="bg-white p-4 rounded-lg shadow">
            <div class="flex items-center justify-between mb-3">
              <div>
                <h3 class="font-bold text-fire-dark">归属对账</h3>
                <p class="text-sm text-gray-500 mt-1">
                  逐件核对「归属单上的当前班组」与「流水末段班组」，对不上的只挑出来列明，不替人改账。
                </p>
              </div>
              <el-button @click="fetchMismatches">
                <RefreshCw class="w-4 h-4 mr-1" />
                刷新
              </el-button>
            </div>
            <div v-if="mismatches.length === 0" class="text-center text-gray-400 py-6">
              全部对得上：每件器材的当前归属都落在流水末段上
            </div>
            <el-table v-else :data="mismatches">
              <el-table-column prop="equipmentCode" label="器材编号" width="150" />
              <el-table-column label="问题" width="160">
                <template #default="{ row }">
                  <el-tag type="danger" size="small">{{ mismatchTypeLabel(row.type) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="assignmentTeamName" label="归属单上的班组" width="140">
                <template #default="{ row }">{{ row.assignmentTeamName ?? '—' }}</template>
              </el-table-column>
              <el-table-column prop="segmentTeamName" label="流水末段班组" width="140">
                <template #default="{ row }">{{ row.segmentTeamName ?? '—' }}</template>
              </el-table-column>
              <el-table-column prop="detail" label="说明" min-width="240" />
            </el-table>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog title="绑定器材到班组" v-model="bindDialogVisible" width="400px">
      <el-form :model="bindForm" label-width="100px">
        <el-form-item label="器材编号">
          <el-input :value="unassignedEquipments.find(e => e.id === bindForm.equipmentId)?.equipmentCode" disabled />
        </el-form-item>
        <el-form-item label="训练用途">
          <el-input :value="unassignedEquipments.find(e => e.id === bindForm.equipmentId)?.trainingPurpose" disabled />
        </el-form-item>
        <el-form-item label="选择班组" required>
          <el-select v-model="bindForm.teamId" placeholder="请选择班组">
            <el-option v-for="team in teams" :key="team.id" :label="teamOptionLabel(team)" :value="team.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="bindForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleBind">绑定</el-button>
      </template>
    </el-dialog>

    <el-dialog title="调整器材归属" v-model="adjustDialogVisible" width="420px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="确认后该器材今天及以后、尚未结束的课目占用将被当场作废。"
        class="mb-3"
      />
      <el-form :model="adjustForm" label-width="90px">
        <el-form-item label="器材编号">
          <el-input :value="adjustForm.equipmentCode" disabled />
        </el-form-item>
        <el-form-item label="当前班组">
          <el-input
            :value="teams.find(t => t.id === adjustForm.currentTeamId)?.teamName"
            disabled
          />
        </el-form-item>
        <el-form-item label="新班组" required>
          <el-select v-model="adjustForm.newTeamId" placeholder="请选择新班组">
            <el-option
              v-for="team in teams.filter(t => t.id !== adjustForm.currentTeamId)"
              :key="team.id"
              :label="teamOptionLabel(team)"
              :value="team.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="调整原因">
          <el-input v-model="adjustForm.reason" type="textarea" :rows="2" placeholder="请输入调整原因" />
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="adjustForm.operator" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAdjust">确认调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>