<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Home, Wrench, Users, Link2, CalendarClock } from 'lucide-vue-next'

const router = useRouter()
const route = useRoute()

const menuItems = [
  { path: '/', icon: Home, label: '统计概览' },
  { path: '/equipment', icon: Wrench, label: '器材管理' },
  { path: '/team', icon: Users, label: '班组管理' },
  { path: '/assignment', icon: Link2, label: '归属绑定' },
  { path: '/occupancy', icon: CalendarClock, label: '课目占用' }
]

const activeIndex = computed(() => route.path)

const handleMenuSelect = (index: string) => {
  router.push(index)
}
</script>

<template>
  <el-layout class="min-h-screen">
    <el-aside width="200" class="bg-fire-dark">
      <div class="text-white text-lg font-bold p-4 border-b border-white/20">
        消防训练基地
      </div>
      <el-menu
        mode="vertical"
        :default-active="activeIndex"
        class="mt-4"
        text-color="#fff"
        active-text-color="#DC143C"
        background-color="#191970"
        @select="handleMenuSelect"
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <component :is="item.icon" class="w-5 h-5 mr-2" />
          {{ item.label }}
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-layout>
      <el-header class="bg-white shadow-sm">
        <div class="flex items-center justify-between h-14 px-6">
          <h1 class="text-xl font-bold text-fire-dark">
            {{ menuItems.find(item => item.path === route.path)?.label || '系统' }}
          </h1>
          <span class="text-sm text-gray-500">管理员</span>
        </div>
      </el-header>
      <el-main class="p-6">
        <router-view />
      </el-main>
    </el-layout>
  </el-layout>
</template>