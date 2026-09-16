<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { Save, RefreshCw, ClipboardList } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { shuttleRunApi, type ShuttleRunScore, type ShuttleRunSummary } from '@/api'

/**
 * 折返跑成绩：合格率不存本地、不信本地计算，一律以服务端实时返回为准 ——
 * 班组行里的合格率与总览合计卡上的总合格率来自同一批接口，保存成功后一起重取，
 * 保存失败（400 合格人数比应测还多 / 409 被并发抢先）时整页数字停在保存前。
 */
const testDate = ref(today())
const rows = ref<ShuttleRunScore[]>([])
const summary = ref<ShuttleRunSummary | null>(null)
const loading = ref(false)

const dialogVisible = ref(false)
const saving = ref(false)
const editing = ref<ShuttleRunScore | null>(null)
const formExpected = ref(0)
const formPassed = ref(0)

function today(): string {
  const d = new Date()
  const m = `${d.getMonth() + 1}`.padStart(2, '0')
  const day = `${d.getDate()}`.padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

const fetchData = async () => {
  loading.value = true
  try {
    const [rowData, summaryData] = await Promise.all([
      shuttleRunApi.overview(testDate.value),
      shuttleRunApi.summary(testDate.value)
    ])
    // 两处数字来自同一次按测验日的服务端取数，天然一致
    rows.value = (rowData as any) ?? []
    summary.value = summaryData as any
  } finally {
    loading.value = false
  }
}

const onDateChange = () => fetchData()

const openCreate = (row: ShuttleRunScore) => {
  editing.value = row
  formExpected.value = row.expectedCount ?? 0
  formPassed.value = row.passedCount ?? 0
  dialogVisible.value = true
}

const rateText = (rate?: number | null) =>
  rate === null || rate === undefined ? '—' : `${(Number(rate) as number).toFixed(1)}%`

const canSave = computed(() => formExpected.value >= 0 && formPassed.value >= 0)

const handleSave = async () => {
  if (!editing.value) return
  if (formPassed.value > formExpected.value) {
    // 与服务端同口径前置拦一道，避免无效请求；服务端仍会再判一次并整单回滚
    ElMessage.error(
      `合格人数（${formPassed.value}）比应测人数（${formExpected.value}）还多，本次改人数不能保存`
    )
    return
  }
  saving.value = true
  const target = editing.value
  try {
    await shuttleRunApi.save(target.teamId, testDate.value, {
      expectedCount: formExpected.value,
      passedCount: formPassed.value,
      operator: '教员',
      version: target.version
    })
    ElMessage.success('成绩已保存，合格率已按新人数重算')
    dialogVisible.value = false
    // 保存成功后班组行与总览合计一起重取，不留半新半旧
    await fetchData()
  } catch (error: any) {
    const status = error?.response?.status
    if (status === 409) {
      // 后写者被整单拦下：先写成功的人数不动，拉最新版回填让教员基于最新人数重改
      ElMessage.error(error.response?.data?.message || '该班组当天的成绩刚被其他人改过，请核对最新人数后重新修改')
    } else {
      // 400：合格人数比应测还多等 —— 本次整单未保存，页面数字保持保存前的值
      ElMessage.error(error.response?.data?.message || '保存失败，成绩未改动')
    }
    await fetchData()
    if (dialogVisible.value) {
      const latest = rows.value.find(r => r.teamId === target.teamId)
      if (latest) {
        editing.value = latest
        formExpected.value = latest.expectedCount ?? formExpected.value
        formPassed.value = latest.passedCount ?? formPassed.value
      }
    }
  } finally {
    saving.value = false
  }
}

onMounted(fetchData)
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between flex-wrap gap-3">
      <h2 class="text-xl font-bold text-fire-dark">折返跑测验成绩</h2>
      <div class="flex items-center gap-3">
        <el-date-picker
          v-model="testDate"
          type="date"
          value-format="YYYY-MM-DD"
          :clearable="false"
          label="测验日"
          @change="onDateChange"
        />
        <el-button :loading="loading" @click="fetchData">
          <RefreshCw class="w-4 h-4 mr-2" />
          刷新
        </el-button>
      </div>
    </div>

    <!-- 训练基地总览：当天合计与总合格率，数字与各班组行同源同次取数 -->
    <el-row :gutter="20">
      <el-col :span="8">
        <el-card class="shadow-lg">
          <el-statistic title="当天应测人数（合计）" :value="summary?.totalExpected ?? 0">
            <template #prefix>
              <ClipboardList class="w-6 h-6 text-fire-dark" />
            </template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card class="shadow-lg">
          <el-statistic title="当天合格人数（合计）" :value="summary?.totalPassed ?? 0" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card class="shadow-lg">
          <div class="text-sm text-gray-500 mb-1">总览当天合格率</div>
          <div class="text-3xl font-bold text-fire-red">
            {{ summary?.passRate === null || summary?.passRate === undefined ? '—' : summary.passRate.toFixed(1) + '%' }}
          </div>
          <div class="mt-2 text-xs text-gray-400">合格人数 ÷ 应测人数，与班组卡片同一口径</div>
        </el-card>
      </el-col>
    </el-row>

    <el-table :data="rows" v-loading="loading" class="bg-white rounded-lg">
      <el-table-column prop="teamName" label="班组" min-width="140" />
      <el-table-column label="测验日" width="130">
        <template #default>{{ testDate }}</template>
      </el-table-column>
      <el-table-column label="应测人数" width="110" align="center">
        <template #default="{ row }">{{ row.expectedCount ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="合格人数" width="110" align="center">
        <template #default="{ row }">{{ row.passedCount ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="合格率" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="row.passRate === null || row.passRate === undefined ? 'info' : 'danger'">
            {{ rateText(row.passRate) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="openCreate(row)">
            <Save class="w-4 h-4 mr-1" />
            {{ row.id ? '改人数' : '记成绩' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      :title="`${editing?.teamName ?? ''} · ${testDate} 折返跑成绩`"
      v-model="dialogVisible"
      @closed="() => { editing = null }"
    >
      <el-form label-width="110px" @submit.prevent>
        <el-form-item label="应测人数" required>
          <el-input-number v-model="formExpected" :min="0" :step="1" />
        </el-form-item>
        <el-form-item label="合格人数" required>
          <el-input-number v-model="formPassed" :min="0" :step="1" />
        </el-form-item>
        <el-alert
          v-if="formPassed > formExpected"
          type="error"
          :closable="false"
          show-icon
          :title="`合格人数（${formPassed}）比应测人数（${formExpected}）还多，这样不能保存`"
          class="mb-3"
        />
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="!canSave || formPassed > formExpected" @click="handleSave">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
