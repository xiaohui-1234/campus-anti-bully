import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import ConfigOverview from '../views/ConfigOverview.vue'
import CacheConfig from '../views/CacheConfig.vue'
import StorageConfig from '../views/StorageConfig.vue'
import MqttConfig from '../views/MqttConfig.vue'
import MqttTestTool from '../views/MqttTestTool.vue'
import SystemInfo from '../views/SystemInfo.vue'
import DeviceManagement from '../views/DeviceManagement.vue'

const routes = [
  { path: '/login', component: LoginView },
  { path: '/', redirect: '/config' },
  { path: '/config', component: ConfigOverview },
  { path: '/cache', component: CacheConfig },
  { path: '/storage', component: StorageConfig },
  { path: '/mqtt', component: MqttConfig },
  { path: '/mqtt-test', component: MqttTestTool },
  { path: '/devices', component: DeviceManagement },
  { path: '/system', component: SystemInfo }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  if (to.path !== '/login' && !localStorage.getItem('admin_access_token')) {
    return '/login'
  }
  return true
})

export default router
