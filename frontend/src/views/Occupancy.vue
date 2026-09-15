<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { RefreshCw, Plus, CalendarClock, Ban } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import {
  occupancyApi,
  teamApi,
  assignmentApi,
  type TrainingOccupancy,
  type Team,
  type Equipment
} from '@/api'

const today = () => {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

const trainingDate = ref(today())
const filterTeamId = ref<number | undefined>(undefined)
const includeCancelled = ref(true)

const teams = ref<Team[]>([])
const tableData = ref<TrainingOccupancy[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(50)
const activeCounts = ref<{ teamId: number; activeCount: number }[]>([])

const dialogVisible = ref(false)
const submitting = ref(false)
const form = ref({
  teamId: undefined as number | undefined,
  equipmentId: undefined as number | undefined,
  trainingDate: today(),
  startTime: '09:00:00',
  endTime: '10:00:00',
  courseName: '',
  operator: ''
})
const teamEquipments = ref<Equipment[]>([])

const teamName = (id?: number) => teams.value.find(t => t.id === id)?.teamName ?? '-'

const countMap = computed(() => {
  const m = new Map<number, number>()
  for (const t of teams.value) m.set(t.id!, 0)
  for (const c of activeCounts.value) m.set(c.teamId, c.activeCount)
  return m
})

const errMsg = (e: any) => e?.response?.data?.message || e?.message || '操作失败'

const fetchList = async () => {
  const result: any = await occupancyApi.list({
    trainingDate: trainingDate.value,
    teamId: filterTeamId.value,
    includeCancelled: includeCancelled.value,
    page: page.value - 1,
    size: pageSize.value
  })
  tableData.value = result.content
  total.value = result.totalElements
}

const fetchCounts = async () => {
  activeCounts.value = (await occupancyApi.activeCounts(trainingDate.value)) as any
}

const fetchAll = async () => {
  await Promise.all([fetchList(), fetchCounts()])
}

const openDialog = async () => {
  form.value = {
    teamId: filterTeamId.value,
    equipmentId: undefined,
    trainingDate: trainingDate.value,
    startTime: '09:00:00',
    endTime: '10:00:00',
    courseName: '',
    operator: ''
  }
  teamEquipments.value = []
  dialogVisible.value = true
  if (form.value.teamId) await loadTeamEquipments(form.value.teamId)
}

const loadTeamEquipments = async (teamId: number) => {
  form.value.equipmentId = undefined
  teamEquipments.value = (await assignmentApi.getByTeam(teamId)) as any
}

const handleSubmit = async () => {
  const f = form.value
  if (!f.teamId) return ElMessage.warning('请选择班长所属班组')
  if (!f.equipmentId) return ElMessage.warning('请选择器材')
  if (!f.courseName.trim()) return ElMessage.warning('请填写课目名称')
  if (!f.startTime || !f.endTime) return ElMessage.warning('请选择开始和结束时间')
  if (f.endTime <= f.startTime) return ElMessage.warning('结束时间必须晚于开始时间')

  submitting.value = true
  try {
    await occupancyApi.create({
      equipmentId: f.equipmentId,
      teamId: f.teamId,
      trainingDate: f.trainingDate,
      startTime: f.startTime,
      endTime: f.endTime,
      courseName: f.courseName.trim(),
      operator: f.operator?.trim() || teamName(f.teamId) + '班长'
    })
    ElMessage.success('课目占用已挂上')
    dialogVisible.value = false
    await fetchAll()
  } catch (e: any) {
    ElMessage.error(errMsg(e))
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  teams.value = (await teamApi.getAll()) as any
  await fetchAll()
})
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <h2 class="text-xl font-bold text-fire-dark">课目占用</h2>
      <div class="flex gap-2">
        <el-button type="primary" @click="openDialog">
          <Plus class="w-4 h-4 mr-1" />
          挂课目占用
        </el-button>
        <el-button @click="fetchAll">
          <RefreshCw class="w-4 h-4 mr-1" />
          刷新
        </el-button>
      </div>
    </div>

    <!-- 班组侧：当天有效占用条数（作废不计） -->
    <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
      <div
        v-for="team in teams"
        :key="team.id"
        class="bg-white rounded-lg shadow p-4 cursor-pointer hover:ring-2 hover:ring-fire transition"
        :class="{ 'ring-2 ring-fire': filterTeamId === team.id }"
        @click="filterTeamId = filterTeamId === team.id ? undefined : team.id"
      >
        <div class="text-sm text-gray-500 flex items-center gap-1">
          <CalendarClock class="w-4 h-4" />
          {{ team.teamName }}
        </div>
        <div class="mt-2 text-2xl font-bold text-fire-dark">{{ countMap.get(team.id!) ?? 0 }}</div>
        <div class="text-xs text-gray-400">{{ trainingDate }} 有效占用</div>
      </div>
    </div>

    <!-- 过滤条件 -->
    <div class="bg-white p-4 rounded-lg shadow flex flex-wrap items-center gap-4">
      <span class="text-sm text-gray-600">训练日期</span>
      <el-date-picker
        v-model="trainingDate"
        type="date"
        value-format="YYYY-MM-DD"
        :clearable="false"
        style="width: 160px"
        @change="fetchAll"
      />
      <span class="text-sm text-gray-600">班组</span>
      <el-select
        v-model="filterTeamId"
        placeholder="全部班组"
        clearable
        style="width: 160px"
        @change="fetchList"
      >
        <el-option v-for="t in teams" :key="t.id" :label="t.teamName" :value="t.id" />
      </el-select>
      <el-switch
        v-model="includeCancelled"
        active-text="显示已作废记录"
        @change="fetchList"
      />
    </div>

    <!-- 占用列表 -->
    <div class="bg-white p-4 rounded-lg shadow">
      <el-table :data="tableData" row-key="id" :row-class-name="({ row }: any) => row.status === 'CANCELLED' ? 'row-cancelled' : ''">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div v-if="row.status === 'CANCELLED'" class="p-3 bg-gray-50 text-sm">
              <Ban class="w-4 h-4 inline mr-1 text-gray-500" />
              <span class="text-gray-600">
                已于 {{ row.cancelTime }} 由 {{ row.cancelOperator }} 作废：{{ row.cancelReason }}
              </span>
            </div>
            <div v-else class="p-3 bg-gray-50 text-sm text-gray-500">
              挂载时间：{{ row.createTime }}　挂载人：{{ row.operator || '-' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="equipmentCode" label="器材编号" width="130" />
        <el-table-column prop="trainingPurpose" label="训练用途" width="140" />
        <el-table-column label="所属班组" width="120">
          <template #default="{ row }">{{ row.teamName }}</template>
        </el-table-column>
        <el-table-column label="时段" width="170">
          <template #default="{ row }">
            {{ row.startTime?.slice(0, 5) }} ~ {{ row.endTime?.slice(0, 5) }}
          </template>
        </el-table-column>
        <el-table-column prop="courseName" label="课目" min-width="150" />
        <el-table-column prop="operator" label="挂载人" width="110" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'ACTIVE'" type="success">有效</el-tag>
            <el-tag v-else type="info">已作废</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="includeCancelled" label="作废痕迹" min-width="220">
          <template #default="{ row }">
            <span v-if="row.status === 'CANCELLED'" class="text-xs text-gray-500">
              {{ row.cancelOperator }} · {{ row.cancelReason }}
            </span>
            <span v-else class="text-gray-300">-</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="flex justify-center mt-4">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @current-change="fetchList"
          @size-change="fetchList"
        />
      </div>
    </div>

    <!-- 挂载对话框 -->
    <el-dialog title="挂课目占用" v-model="dialogVisible" width="460px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="所属班组" required>
          <el-select
            v-model="form.teamId"
            placeholder="选择班长所属班组"
            style="width: 100%"
            @change="loadTeamEquipments"
          >
            <el-option v-for="t in teams" :key="t.id" :label="t.teamName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="器材" required>
          <el-select
            v-model="form.equipmentId"
            :placeholder="form.teamId ? '只能选择本班组名下器材' : '请先选择班组'"
            :disabled="!form.teamId"
            style="width: 100%"
          >
            <el-option
              v-for="e in teamEquipments"
              :key="e.id"
              :label="`${e.equipmentCode}（${e.trainingPurpose}）`"
              :value="e.id"
            />
          </el-select>
          <div v-if="form.teamId && teamEquipments.length === 0" class="text-xs text-gray-400 mt-1">
            该班组名下暂无器材
          </div>
        </el-form-item>
        <el-form-item label="训练日期" required>
          <el-date-picker
            v-model="form.trainingDate"
            type="date"
            value-format="YYYY-MM-DD"
            :clearable="false"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-time-picker
            v-model="form.startTime"
            value-format="HH:mm:ss"
            format="HH:mm"
            :clearable="false"
            placeholder="开始"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-time-picker
            v-model="form.endTime"
            value-format="HH:mm:ss"
            format="HH:mm"
            :clearable="false"
            placeholder="结束"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="课目名称" required>
          <el-input v-model="form.courseName" placeholder="如：水带铺设训练" />
        </el-form-item>
        <el-form-item label="班长">
          <el-input v-model="form.operator" placeholder="挂载人，默认 班组名+班长" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">挂上</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
:deep(.row-cancelled) {
  color: #9ca3af;
  text-decoration: line-through;
}
:deep(.row-cancelled .el-tag) {
  text-decoration: none;
}
</style>
