<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { Package, Users, TrendingUp, BarChart3 } from 'lucide-vue-next'
import * as echarts from 'echarts'
import { statisticsApi, type OverviewStatistics, type TeamStatistics } from '@/api'

const router = useRouter()
const overview = ref<OverviewStatistics | null>(null)
const teamStats = ref<TeamStatistics[]>([])
const chartRef = ref<HTMLDivElement | null>(null)
let chartInstance: echarts.ECharts | null = null

const fetchData = async () => {
  overview.value = await statisticsApi.getOverview() as any
  teamStats.value = await statisticsApi.getTeamStatistics() as any
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

// 首页挂着的期间定时重取：占用刚挂上 / 外借刚出门提交后，
// 开着的首页自己就会掉数，不用等手动刷新（后端每次实时算，不落缓存）
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
      <h3 class="text-lg font-bold mb-4">班组可训器材详情</h3>
      <div class="space-y-3">
        <div
          v-for="stat in teamStats"
          :key="stat.teamId"
          class="flex items-center justify-between p-3 bg-gray-50 rounded-lg"
        >
          <div class="flex items-center">
            <div class="w-3 h-3 bg-fire-red rounded-full mr-3"></div>
            <span class="font-medium">{{ stat.teamName }}</span>
          </div>
          <div class="flex items-center gap-4">
            <span class="text-xs text-gray-400">名下 {{ stat.assignedCount }} 件</span>
            <span class="text-gray-500">可训器材</span>
            <span class="text-xl font-bold text-fire-red">{{ stat.equipmentCount }}</span>
          </div>
        </div>
        <div v-if="teamStats.length === 0" class="text-center text-gray-400 py-8">
          暂无数据
        </div>
      </div>
    </el-card>
  </div>
</template>