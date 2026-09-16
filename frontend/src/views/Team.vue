<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Edit, Delete, Search } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { teamApi, shuttleRunApi, type Team, type ShuttleRunScore } from '@/api'

/**
 * 班组页新增“合格人数”列：按所选测验日，展示每个班组当天折返跑合格人数。
 * 数字全部实时取后端（与总览同一本账），本页不缓存、不自行推算合格率。
 */
const router = useRouter()
const tableData = ref<Team[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const deleteDialogVisible = ref(false)
const deleteId = ref<number | null>(null)

const testDate = ref(today())
const scoreMap = ref<Record<number, ShuttleRunScore>>({})

function today(): string {
  const d = new Date()
  const m = `${d.getMonth() + 1}`.padStart(2, '0')
  const day = `${d.getDate()}`.padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

const fetchData = async () => {
  const params = {
    page: currentPage.value - 1,
    size: pageSize.value,
    keyword: keyword.value || undefined
  }
  const result: any = await teamApi.getList(params)
  tableData.value = result.content
  total.value = result.totalElements
  await fetchScores()
}

// 按测验日取全部班组的成绩，回填到班组行；当天没记成绩的班组显示“—”
const fetchScores = async () => {
  const rows = (await shuttleRunApi.overview(testDate.value)) as any as ShuttleRunScore[]
  const map: Record<number, ShuttleRunScore> = {}
  for (const row of rows) {
    if (row.teamId != null && row.id != null) {
      map[row.teamId] = row
    }
  }
  scoreMap.value = map
}

const handleSearch = () => {
  currentPage.value = 1
  fetchData()
}

const handleDelete = (id: number) => {
  deleteId.value = id
  deleteDialogVisible.value = true
}

const confirmDelete = async () => {
  if (deleteId.value) {
    await teamApi.delete(deleteId.value)
    deleteDialogVisible.value = false
    fetchData()
    ElMessage.success('删除成功')
  }
}

onMounted(() => {
  fetchData()
})
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between">
      <h2 class="text-xl font-bold text-fire-dark">班组管理</h2>
      <el-button type="primary" @click="router.push('/team/add')">
        <Plus class="w-4 h-4 mr-2" />
        新增班组
      </el-button>
    </div>

    <div class="flex items-center gap-4 flex-wrap">
      <el-input
        v-model="keyword"
        placeholder="搜索班组名称"
        style="width: 300px"
        @keyup.enter="handleSearch"
      >
        <template #prefix>
          <Search class="w-4 h-4" />
        </template>
      </el-input>
      <el-button @click="handleSearch">搜索</el-button>
      <div class="flex items-center gap-2 ml-auto">
        <span class="text-sm text-gray-500">折返跑测验日</span>
        <el-date-picker
          v-model="testDate"
          type="date"
          value-format="YYYY-MM-DD"
          :clearable="false"
          @change="fetchScores"
        />
      </div>
    </div>

    <el-table :data="tableData" v-loading>
      <el-table-column prop="teamName" label="班组名称" width="150" />
      <el-table-column prop="memberCount" label="成员数量" width="100" />
      <el-table-column label="合格人数" width="130" align="center">
        <template #default="{ row }">
          <span v-if="scoreMap[row.id]">
            {{ scoreMap[row.id].passedCount }}
            <span class="text-xs text-gray-400">/ {{ scoreMap[row.id].expectedCount }} 人</span>
          </span>
          <span v-else class="text-gray-300">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="180" />
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <div class="flex gap-2">
            <el-button size="small" @click="router.push(`/team/edit/${row.id}`)">
              <Edit class="w-4 h-4" />
            </el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.id!)">
              <Delete class="w-4 h-4" />
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <div class="flex justify-center">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="fetchData"
        @size-change="fetchData"
      />
    </div>

    <el-dialog title="确认删除" v-model="deleteDialogVisible">
      <p>确定要删除该班组吗？</p>
      <template #footer>
        <el-button @click="deleteDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmDelete">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>
