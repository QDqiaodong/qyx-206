<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { equipmentApi, type Equipment } from '@/api'

const route = useRoute()
const router = useRouter()
const isEdit = ref(false)
const form = ref<Equipment>({
  equipmentCode: '',
  trainingPurpose: '',
  sizeSpec: ''
})

onMounted(() => {
  const id = route.params.id
  if (id) {
    isEdit.value = true
    equipmentApi.getById(Number(id)).then((data: any) => {
      form.value = data
    })
  }
})

const handleSubmit = async () => {
  try {
    if (isEdit.value) {
      await equipmentApi.update(form.value.id!, form.value)
      ElMessage.success('更新成功')
    } else {
      await equipmentApi.create(form.value)
      ElMessage.success('创建成功')
    }
    router.push('/equipment')
  } catch (error) {
    ElMessage.error('操作失败')
  }
}
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center gap-4">
      <el-button @click="router.push('/equipment')">
        <ArrowLeft class="w-4 h-4 mr-2" />
        返回
      </el-button>
      <h2 class="text-xl font-bold text-fire-dark">{{ isEdit ? '编辑器材' : '新增器材' }}</h2>
    </div>

    <div class="bg-white p-6 rounded-lg shadow">
      <el-form :model="form" label-width="120px">
        <el-form-item label="器材编号" required>
          <el-input v-model="form.equipmentCode" placeholder="请输入器材编号" />
        </el-form-item>
        <el-form-item label="训练用途" required>
          <el-input v-model="form.trainingPurpose" placeholder="请输入训练用途" />
        </el-form-item>
        <el-form-item label="尺寸规格">
          <el-input v-model="form.sizeSpec" placeholder="请输入尺寸规格" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSubmit">提交</el-button>
          <el-button @click="router.push('/equipment')">取消</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>