import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Overview',
    component: () => import('@/views/Overview.vue')
  },
  {
    path: '/equipment',
    name: 'Equipment',
    component: () => import('@/views/Equipment.vue')
  },
  {
    path: '/equipment/add',
    name: 'EquipmentAdd',
    component: () => import('@/views/EquipmentForm.vue')
  },
  {
    path: '/equipment/edit/:id',
    name: 'EquipmentEdit',
    component: () => import('@/views/EquipmentForm.vue')
  },
  {
    path: '/team',
    name: 'Team',
    component: () => import('@/views/Team.vue')
  },
  {
    path: '/team/add',
    name: 'TeamAdd',
    component: () => import('@/views/TeamForm.vue')
  },
  {
    path: '/team/edit/:id',
    name: 'TeamEdit',
    component: () => import('@/views/TeamForm.vue')
  },
  {
    path: '/assignment',
    name: 'Assignment',
    component: () => import('@/views/Assignment.vue')
  },
  {
    path: '/occupancy',
    name: 'Occupancy',
    component: () => import('@/views/Occupancy.vue')
  },
  {
    path: '/loan',
    name: 'Loan',
    component: () => import('@/views/Loan.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router