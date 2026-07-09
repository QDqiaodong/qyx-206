<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Edit, Delete, Search } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { teamApi, type Team } from '@/api'

const router = useRouter()
const tableData = ref<Team[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const deleteDialogVisible = ref(false)
const deleteId = ref<number | null>(null)

const fetchData = async () => {
  const params = {
    page: currentPage.value - 1,
    size: pageSize.value,
    keyword: keyword.value || undefined
  }
  const result: any = await teamApi.getList(params)
  tableData.value = result.content
  total.value = result.totalElements
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

    <div class="flex items-center gap-4">
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
    </div>

    <el-table :data="tableData" v-loading>
      <el-table-column prop="teamName" label="班组名称" width="150" />
      <el-table-column prop="memberCount" label="成员数量" width="100" />
      <el-table-column prop="description" label="描述" min-width="200" />
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