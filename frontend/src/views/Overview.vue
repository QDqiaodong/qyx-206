<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { Package, Users, TrendingUp, BarChart3, ClipboardCheck } from 'lucide-vue-next'
import * as echarts from 'echarts'
import { statisticsApi, shuttleRunApi, type OverviewStatistics, type TeamStatistics, type ShuttleRunScore, type ShuttleRunSummary } from '@/api'

const router = useRouter()
const overview = ref<OverviewStatistics | null>(null)
const teamStats = ref<TeamStatistics[]>([])
const chartRef = ref<HTMLDivElement | null>(null)
let chartInstance: echarts.ECharts | null = null

// 折返跑合格率：班组卡片与总览合计都取后端同一批实时数据，两处数字天然一致。
// 合格率绝不本地另算，改合格人数保存成功后这里重取就是新率；保存失败时停在旧率。
const shuttleDate = ref(today())
const shuttleRows = ref<ShuttleRunScore[]>([])
const shuttleSummary = ref<ShuttleRunSummary | null>(null)

function today(): string {
  const d = new Date()
  const m = `${d.getMonth() + 1}`.padStart(2, '0')
  const day = `${d.getDate()}`.padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

const rateText = (rate?: number | null) =>
  rate === null || rate === undefined ? '—' : `${Number(rate).toFixed(1)}%`

const fetchShuttle = async () => {
  const [rows, summary] = await Promise.all([
    shuttleRunApi.overview(shuttleDate.value),
    shuttleRunApi.summary(shuttleDate.value)
  ])
  shuttleRows.value = (rows as any) ?? []
  shuttleSummary.value = summary as any
}

const fetchData = async () => {
  overview.value = await statisticsApi.getOverview() as any
  teamStats.value = await statisticsApi.getTeamStatistics() as any
  await fetchShuttle()
  await nextTick()
  initChart()
}

const initChart = () => {
  if (!chartRef.value) return
  if (chartInstance) {
    chartInstance.dispose()
  }
  chartInstance = echarts.init(chartRef.value)
  const option: echarts.EChartsOption = {
    title: {
      text: '各班组可训器材统计',
      left: 'center',
      textStyle: { color: '#333' }
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: teamStats.value.map(s => s.teamName),
      axisLabel: { rotate: 30 }
    },
    yAxis: {
      type: 'value',
      name: '可训器材'
    },
    series: [
      {
        name: '可训器材',
        type: 'bar',
        data: teamStats.value.map(s => s.equipmentCount),
        itemStyle: {
          color: '#DC143C',
          borderRadius: [4, 4, 0, 0]
        }
      }
    ]
  }
  chartInstance.setOption(option)
}

// 首页挂着的期间定时重取：占用刚挂上 / 外借刚出门 / 合格人数刚改完提交后，
// 开着的首页自己就会更新，不用等手动刷新（后端每次实时算，不落缓存）
let timer: number | undefined

onMounted(() => {
  fetchData()
  timer = window.setInterval(fetchData, 15_000)
})

onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between">
      <h2 class="text-2xl font-bold text-fire-dark">统计概览</h2>
      <div class="flex gap-3">
        <el-button type="primary" @click="router.push('/equipment')">
          <Package class="w-4 h-4 mr-2" />
          器材管理
        </el-button>
        <el-button @click="router.push('/team')">
          <Users class="w-4 h-4 mr-2" />
          班组管理
        </el-button>
        <el-button @click="router.push('/assignment')">
          <BarChart3 class="w-4 h-4 mr-2" />
          归属绑定
        </el-button>
      </div>
    </div>

    <el-row :gutter="20">
      <el-col :span="6">
        <el-card class="shadow-lg">
          <el-statistic title="在库可训器材" :value="overview?.totalEquipment || 0">
            <template #prefix>
              <Package class="w-6 h-6 text-fire-red" />
            </template>
          </el-statistic>
          <div class="mt-2 text-xs text-gray-400">
            登记 {{ overview?.registeredEquipment || 0 }} 件 ·
            {{ overview?.unavailableEquipment || 0 }} 件占用/在外不计入
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="shadow-lg">
          <el-statistic title="总班组数" :value="overview?.totalTeam || 0">
            <template #prefix>
              <Users class="w-6 h-6 text-fire-dark" />
            </template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="shadow-lg">
          <el-statistic title="班均可训器材" :value="overview?.averageEquipmentPerTeam || 0" :precision="1">
            <template #prefix>
              <TrendingUp class="w-6 h-6 text-green-600" />
            </template>
          </el-statistic>
          <div class="mt-2 text-xs text-gray-400">在库可训器材 ÷ 班组数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="shadow-lg">
          <el-statistic title="统计更新" value="实时" suffix="自动更新">
            <template #prefix>
              <BarChart3 class="w-6 h-6 text-blue-600" />
            </template>
          </el-statistic>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="shadow-lg">
      <div ref="chartRef" class="w-full h-64"></div>
    </el-card>

    <el-card class="shadow-lg">
      <div class="flex items-center justify-between mb-4 flex-wrap gap-3">
        <h3 class="text-lg font-bold flex items-center">
          <ClipboardCheck class="w-5 h-5 mr-2 text-fire-red" />
          折返跑测验合格率
        </h3>
        <div class="flex items-center gap-3">
          <el-date-picker
            v-model="shuttleDate"
            type="date"
            value-format="YYYY-MM-DD"
            :clearable="false"
            @change="fetchShuttle"
          />
          <el-tag size="large" type="danger">
            总览合格率：{{ rateText(shuttleSummary?.passRate) }}
          </el-tag>
          <el-tag size="large" type="info">
            合格 {{ shuttleSummary?.totalPassed ?? 0 }} / 应测 {{ shuttleSummary?.totalExpected ?? 0 }}
          </el-tag>
        </div>
      </div>
      <div class="space-y-3">
        <div
          v-for="row in shuttleRows"
          :key="row.teamId"
          class="p-3 bg-gray-50 rounded-lg flex items-center justify-between"
        >
          <div class="flex items-center">
            <div class="w-3 h-3 bg-fire-red rounded-full mr-3"></div>
            <span class="font-medium">{{ row.teamName }}</span>
            <el-tag size="small" type="info" class="ml-2">{{ shuttleDate }}</el-tag>
          </div>
          <div class="flex items-center gap-4">
            <span class="text-xs text-gray-400">
              合格 {{ row.passedCount ?? '—' }} / 应测 {{ row.expectedCount ?? '—' }}
            </span>
            <span class="text-gray-500">合格率</span>
            <span class="text-xl font-bold text-fire-red w-20 text-right">{{ rateText(row.passRate) }}</span>
          </div>
        </div>
        <div v-if="shuttleRows.length === 0" class="text-center text-gray-400 py-8">
          当天暂无班组
        </div>
      </div>
      <div class="mt-2 text-xs text-gray-400">
        合格率由后端按合格人数 ÷ 应测人数实时算出，与“折返跑成绩”页班组卡片同一口径；保存失败时两处都停在旧率。
      </div>
    </el-card>

    <el-card class="shadow-lg">
      <h3 class="text-lg font-bold mb-4">班组可训器材详情</h3>
      <div class="space-y-3">
        <div
          v-for="stat in teamStats"
          :key="stat.teamId"
          class="p-3 bg-gray-50 rounded-lg"
        >
          <div class="flex items-center justify-between">
            <div class="flex items-center">
              <div class="w-3 h-3 bg-fire-red rounded-full mr-3"></div>
              <span class="font-medium">{{ stat.teamName }}</span>
              <el-tag size="small" type="info" class="ml-2">在编制 {{ stat.memberCount ?? 0 }} 人</el-tag>
            </div>
            <div class="flex items-center gap-4">
              <span class="text-xs text-gray-400">名下 {{ stat.assignedCount }} 件</span>
              <span class="text-gray-500">可训器材</span>
              <span class="text-xl font-bold text-fire-red">{{ stat.equipmentCount }}</span>
            </div>
          </div>
          <div v-if="stat.description" class="mt-1 ml-6 text-xs text-gray-500">
            职责：{{ stat.description }}
          </div>
        </div>
        <div v-if="teamStats.length === 0" class="text-center text-gray-400 py-8">
          暂无数据
        </div>
      </div>
    </el-card>
  </div>
</template>