<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Link2, RefreshCw } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { assignmentApi, teamApi, type Equipment, type Team } from '@/api'

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
            <el-table-column prop="equipmentCode" label="器材编号" width="150" />
            <el-table-column prop="oldTeamName" label="原班组" width="120" />
            <el-table-column prop="newTeamName" label="新班组" width="120" />
            <el-table-column prop="changeTime" label="变更时间" width="180" />
            <el-table-column prop="operator" label="操作人" width="100" />
            <el-table-column prop="reason" label="变更原因" min-width="200" />
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
            <el-option v-for="team in teams" :key="team.id" :label="team.teamName" :value="team.id" />
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
              :label="team.teamName"
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