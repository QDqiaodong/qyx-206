<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { RefreshCw, Plus, LogOut, Clock, AlertTriangle, CheckCircle2 } from 'lucide-vue-next'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  loanApi,
  teamApi,
  assignmentApi,
  type EquipmentLoan,
  type Team,
  type Equipment
} from '@/api'

type Tab = 'OPEN' | 'OVERDUE' | 'RETURNED' | 'ALL'

const tabs: { key: Tab; label: string }[] = [
  { key: 'OPEN', label: '在外未还' },
  { key: 'OVERDUE', label: '超期件' },
  { key: 'RETURNED', label: '已归还' },
  { key: 'ALL', label: '全部记录' }
]

const activeTab = ref<Tab>('OPEN')
const filterTeamId = ref<number | undefined>(undefined)

const teams = ref<Team[]>([])
const tableData = ref<EquipmentLoan[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const summary = ref({ openCount: 0, overdueCount: 0, returnedCount: 0 })

const dialogVisible = ref(false)
const submitting = ref(false)
const form = ref({
  teamId: undefined as number | undefined,
  equipmentId: undefined as number | undefined,
  expectedReturnTime: '' as string,
  companions: '',
  reason: '',
  operator: ''
})
const teamEquipments = ref<Equipment[]>([])

const errMsg = (e: any) => e?.response?.data?.message || e?.message || '操作失败'

// 班组下拉选项与班组档案同版：名称后跟在编制与职责说明
const teamOptionLabel = (t: Team) => {
  let label = `${t.teamName}（编制 ${t.memberCount ?? 0} 人`
  if (t.description && t.description.trim()) label += ` · ${t.description.trim()}`
  return label + '）'
}

let timer: number | undefined

const fetchList = async () => {
  const result: any = await loanApi.list({
    tab: activeTab.value,
    teamId: filterTeamId.value,
    page: page.value - 1,
    size: pageSize.value
  })
  tableData.value = result.content
  total.value = result.totalElements
}

const fetchSummary = async () => {
  summary.value = (await loanApi.summary()) as any
}

const fetchAll = async () => {
  await Promise.all([fetchList(), fetchSummary()])
}

const switchTab = (tab: Tab) => {
  activeTab.value = tab
  page.value = 1
  fetchList()
}

const openDialog = async () => {
  form.value = {
    teamId: filterTeamId.value,
    equipmentId: undefined,
    expectedReturnTime: '',
    companions: '',
    reason: '',
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
  if (!f.equipmentId) return ElMessage.warning('请选择要离场的器材（只能选本班组名下器材）')
  if (!f.expectedReturnTime) return ElMessage.warning('请选择预计归还时刻')
  if (!f.companions.trim()) return ElMessage.warning('请填写同行人，缺一项不能出门')
  if (!f.reason.trim()) return ElMessage.warning('请填写离场事由，缺一项不能出门')

  submitting.value = true
  try {
    await loanApi.checkout({
      equipmentId: f.equipmentId,
      teamId: f.teamId,
      expectedReturnTime: f.expectedReturnTime,
      companions: f.companions.trim(),
      reason: f.reason.trim(),
      operator: f.operator.trim() || undefined
    })
    ElMessage.success('离场登记成功，器材已出门')
    dialogVisible.value = false
    await fetchAll()
  } catch (e: any) {
    ElMessage.error(errMsg(e))
  } finally {
    submitting.value = false
  }
}

const handleReturn = async (row: EquipmentLoan) => {
  try {
    const { value } = await ElMessageBox.prompt(
      `确认为器材 ${row.equipmentCode} 办理回场归还？${row.overdue ? '该件已超期未还。' : ''}`,
      '器材回场',
      {
        confirmButtonText: '确认归还',
        cancelButtonText: '取消',
        inputPlaceholder: '归还备注（可选，如：外观完好）',
        inputValue: ''
      }
    )
    await loanApi.returnLoan(row.id!, { remark: value || undefined })
    ElMessage.success('已归还，离场单闭环')
    await fetchAll()
  } catch (e: any) {
    if (e === 'cancel') return
    ElMessage.error(errMsg(e))
  }
}

const fmt = (s?: string) => (s ? s.replace('T', ' ').slice(0, 16) : '-')

onMounted(async () => {
  teams.value = (await teamApi.getAll()) as any
  await fetchAll()
  // 每分钟拉一次，超期状态能在页面上及时翻出来
  timer = window.setInterval(fetchAll, 60_000)
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <h2 class="text-xl font-bold text-fire-dark">器材外借离场</h2>
      <div class="flex gap-2">
        <el-button type="primary" @click="openDialog">
          <Plus class="w-4 h-4 mr-1" />
          登记离场
        </el-button>
        <el-button @click="fetchAll">
          <RefreshCw class="w-4 h-4 mr-1" />
          刷新
        </el-button>
      </div>
    </div>

    <!-- 对账卡片 -->
    <div class="grid grid-cols-3 gap-4">
      <div class="bg-white rounded-lg shadow p-4">
        <div class="text-sm text-gray-500 flex items-center gap-1">
          <LogOut class="w-4 h-4" /> 在外未还
        </div>
        <div class="mt-2 text-2xl font-bold text-fire-dark">{{ summary.openCount }}</div>
        <div class="text-xs text-gray-400">含超期 {{ summary.overdueCount }} 件</div>
      </div>
      <div class="bg-white rounded-lg shadow p-4">
        <div class="text-sm text-gray-500 flex items-center gap-1">
          <AlertTriangle class="w-4 h-4" /> 超期未还
        </div>
        <div class="mt-2 text-2xl font-bold" :class="summary.overdueCount > 0 ? 'text-fire' : 'text-fire-dark'">
          {{ summary.overdueCount }}
        </div>
        <div class="text-xs text-gray-400">归还前不能再次外借</div>
      </div>
      <div class="bg-white rounded-lg shadow p-4">
        <div class="text-sm text-gray-500 flex items-center gap-1">
          <CheckCircle2 class="w-4 h-4" /> 已归还
        </div>
        <div class="mt-2 text-2xl font-bold text-fire-dark">{{ summary.returnedCount }}</div>
        <div class="text-xs text-gray-400">离场闭环累计</div>
      </div>
    </div>

    <!-- 过滤条件 -->
    <div class="bg-white p-4 rounded-lg shadow flex flex-wrap items-center gap-4">
      <el-radio-group :model-value="activeTab" @change="(v: any) => switchTab(v)">
        <el-radio-button v-for="t in tabs" :key="t.key" :value="t.key">{{ t.label }}</el-radio-button>
      </el-radio-group>
      <span class="text-sm text-gray-600 ml-2">班组</span>
      <el-select
        v-model="filterTeamId"
        placeholder="全部班组"
        clearable
        style="width: 160px"
        @change="() => { page = 1; fetchList() }"
      >
        <el-option v-for="t in teams" :key="t.id" :label="teamOptionLabel(t)" :value="t.id" />
      </el-select>
    </div>

    <!-- 离场列表 -->
    <div class="bg-white p-4 rounded-lg shadow">
      <el-table :data="tableData" row-key="id">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="p-3 bg-gray-50 text-sm space-y-1">
              <div><span class="text-gray-400">离场事由：</span>{{ row.reason }}</div>
              <div><span class="text-gray-400">同行人：</span>{{ row.companions }}</div>
              <div><span class="text-gray-400">出门时间：</span>{{ fmt(row.checkoutTime) }}　<span class="text-gray-400">登记班长：</span>{{ row.operator || '-' }}</div>
              <div v-if="row.effectiveStatus === 'RETURNED'">
                <span class="text-gray-400">归还：</span>{{ fmt(row.returnTime) }} 由 {{ row.returnOperator || '-' }} 经手
                <span v-if="row.returnRemark">（{{ row.returnRemark }}）</span>
              </div>
              <div v-else-if="row.overdue" class="text-fire">
                <Clock class="w-4 h-4 inline mr-1" />
                已超过预计归还时刻{{ row.overdueTime ? `（${fmt(row.overdueTime)} 置超期）` : '' }}，器材回来前不能再开离场
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="equipmentCode" label="器材编号" width="140" />
        <el-table-column prop="trainingPurpose" label="训练用途" width="130" />
        <el-table-column label="出门班组" width="120">
          <template #default="{ row }">{{ row.teamName }}</template>
        </el-table-column>
        <el-table-column label="出门时间" width="150">
          <template #default="{ row }">{{ fmt(row.checkoutTime) }}</template>
        </el-table-column>
        <el-table-column label="预计归还" width="150">
          <template #default="{ row }">
            <span :class="{ 'text-fire font-semibold': row.overdue }">{{ fmt(row.expectedReturnTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="companions" label="同行人" min-width="140" show-overflow-tooltip />
        <el-table-column prop="reason" label="离场事由" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.effectiveStatus === 'OUT'" type="warning">在外</el-tag>
            <el-tag v-else-if="row.effectiveStatus === 'OVERDUE'" type="danger">超期</el-tag>
            <el-tag v-else type="success">已归还</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.effectiveStatus !== 'RETURNED'"
              type="primary"
              size="small"
              @click="handleReturn(row)"
            >
              归还
            </el-button>
            <span v-else class="text-gray-300 text-sm">-</span>
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

    <!-- 登记离场对话框 -->
    <el-dialog title="器材外借离场登记" v-model="dialogVisible" width="500px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="预计归还时刻、同行人、离场事由为必填，缺一项不能出门；挂有未结束课目占用的器材会被直接拒绝。"
        class="mb-4"
      />
      <el-form :model="form" label-width="100px">
        <el-form-item label="班长班组" required>
          <el-select
            v-model="form.teamId"
            placeholder="选择班长所属班组"
            style="width: 100%"
            @change="loadTeamEquipments"
          >
            <el-option v-for="t in teams" :key="t.id" :label="teamOptionLabel(t)" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="离场器材" required>
          <el-select
            v-model="form.equipmentId"
            :placeholder="form.teamId ? '只能选择本班组名下器材' : '请先选择班组'"
            :disabled="!form.teamId"
            filterable
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
        <el-form-item label="预计归还" required>
          <el-date-picker
            v-model="form.expectedReturnTime"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            format="YYYY-MM-DD HH:mm"
            :clearable="false"
            placeholder="选择预计归还时刻"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="同行人" required>
          <el-input v-model="form.companions" placeholder="如：张三、李四（必填）" />
        </el-form-item>
        <el-form-item label="离场事由" required>
          <el-input
            v-model="form.reason"
            type="textarea"
            :rows="2"
            placeholder="如：携水带、空气呼吸器赴外场演练（必填）"
          />
        </el-form-item>
        <el-form-item label="登记班长">
          <el-input v-model="form.operator" placeholder="可空，默认由后端留存" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确认出门</el-button>
      </template>
    </el-dialog>
  </div>
</template>
