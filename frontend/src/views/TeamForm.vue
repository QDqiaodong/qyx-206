<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { teamApi, type Team } from '@/api'

const route = useRoute()
const router = useRouter()
const isEdit = ref(false)
const form = ref<Team>({
  teamName: '',
  memberCount: 0,
  description: ''
})

onMounted(() => {
  const id = route.params.id
  if (id) {
    isEdit.value = true
    teamApi.getById(Number(id)).then((data: any) => {
      form.value = data
    })
  }
})

const handleSubmit = async () => {
  try {
    if (isEdit.value) {
      await teamApi.update(form.value.id!, form.value)
      ElMessage.success('更新成功')
    } else {
      await teamApi.create(form.value)
      ElMessage.success('创建成功')
    }
    router.push('/team')
  } catch (error) {
    ElMessage.error('操作失败')
  }
}
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center gap-4">
      <el-button @click="router.push('/team')">
        <ArrowLeft class="w-4 h-4 mr-2" />
        返回
      </el-button>
      <h2 class="text-xl font-bold text-fire-dark">{{ isEdit ? '编辑班组' : '新增班组' }}</h2>
    </div>

    <div class="bg-white p-6 rounded-lg shadow">
      <el-form :model="form" label-width="120px">
        <el-form-item label="班组名称" required>
          <el-input v-model="form.teamName" placeholder="请输入班组名称" />
        </el-form-item>
        <el-form-item label="成员数量" required>
          <el-input-number v-model="form.memberCount" :min="0" placeholder="请输入成员数量" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" placeholder="请输入描述" :rows="3" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSubmit">提交</el-button>
          <el-button @click="router.push('/team')">取消</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>