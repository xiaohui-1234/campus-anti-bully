<template>
  <el-container class="shell">
    <el-aside width="248px" class="aside">
      <div class="brand">
        <div class="brand-mark">防</div>
        <div>
          <div class="brand-title">校园防霸凌</div>
          <div class="brand-subtitle">管理控制台</div>
        </div>
      </div>
      <div class="menu-label">管理</div>
      <el-menu :default-active="$route.path" router class="menu">
        <el-menu-item index="/config">
          <el-icon><Setting /></el-icon>
          <span>配置总览</span>
        </el-menu-item>
        <el-menu-item index="/cache">
          <el-icon><Timer /></el-icon>
          <span>缓存配置</span>
        </el-menu-item>
        <el-menu-item index="/storage">
          <el-icon><FolderOpened /></el-icon>
          <span>存储配置</span>
        </el-menu-item>
        <el-menu-item index="/mqtt">
          <el-icon><Connection /></el-icon>
          <span>MQTT 配置</span>
        </el-menu-item>
        <el-menu-item index="/mqtt-test">
          <el-icon><Operation /></el-icon>
          <span>硬件测试工具</span>
        </el-menu-item>
        <el-menu-item index="/devices">
          <el-icon><Cpu /></el-icon>
          <span>设备管理</span>
        </el-menu-item>
        <div class="menu-label menu-label-secondary">工具与系统</div>
        <el-menu-item index="/system">
          <el-icon><Monitor /></el-icon>
          <span>系统信息</span>
        </el-menu-item>
      </el-menu>
      <div class="aside-footer">
        <div class="admin-avatar">A</div>
        <div>
          <strong>系统管理员</strong>
          <span>ADMIN</span>
        </div>
      </div>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-context">
          <span>管理控制台</span>
          <strong>{{ currentTitle }}</strong>
        </div>
        <el-tooltip content="退出登录" placement="bottom">
          <el-button :icon="SwitchButton" circle @click="logout" />
        </el-tooltip>
      </el-header>
      <el-main class="main">
        <slot />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Connection, Cpu, FolderOpened, Monitor, Operation, Setting, SwitchButton, Timer } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const pageTitles = {
  '/config': '配置总览',
  '/cache': '缓存配置',
  '/storage': '存储配置',
  '/mqtt': 'MQTT 配置',
  '/mqtt-test': '硬件测试工具',
  '/devices': '设备管理',
  '/system': '系统信息'
}
const currentTitle = computed(() => pageTitles[route.path] || '管理后台')

function logout() {
  localStorage.removeItem('admin_access_token')
  localStorage.removeItem('admin_refresh_token')
  router.push('/login')
}
</script>

<style scoped>
.shell {
  min-height: 100vh;
}

.aside {
  position: relative;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border-right: 1px solid #e5e7eb;
}

.brand {
  height: 76px;
  padding: 17px 18px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid #eef0f4;
}

.brand-mark {
  width: 42px;
  height: 42px;
  border-radius: 8px;
  background: #0f766e;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  box-shadow: 0 8px 18px rgba(15, 118, 110, 0.2);
}

.brand-title {
  font-size: 15px;
  font-weight: 800;
  color: #111827;
}

.brand-subtitle {
  margin-top: 3px;
  color: #667085;
  font-size: 12px;
}

.menu {
  flex: 1;
  padding: 0 10px;
  border-right: 0;
}

.menu-label {
  padding: 20px 20px 8px;
  color: #98a2b3;
  font-size: 11px;
  font-weight: 700;
}

.menu-label-secondary {
  padding: 20px 10px 8px;
}

.aside-footer {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 12px;
  padding: 12px;
  border: 1px solid #edf0f3;
  border-radius: 8px;
  background: #fafbfc;
}

.admin-avatar {
  width: 34px;
  height: 34px;
  border-radius: 7px;
  background: #e7f4f1;
  color: #0f766e;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 800;
}

.aside-footer > div:last-child {
  display: grid;
  gap: 3px;
}

.aside-footer strong {
  color: #344054;
  font-size: 12px;
}

.aside-footer span {
  color: #98a2b3;
  font-size: 10px;
  font-weight: 700;
}

.header {
  height: 68px;
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #475467;
}

.header-context {
  display: grid;
  gap: 3px;
}

.header-context span {
  color: #98a2b3;
  font-size: 11px;
}

.header-context strong {
  color: #344054;
  font-size: 14px;
}

.main {
  padding: 28px 30px 36px;
}

:deep(.menu .el-menu-item) {
  height: 44px;
  margin-bottom: 4px;
  border-radius: 7px;
  color: #475467;
}

:deep(.menu .el-menu-item:hover) {
  background: #f3f7f6;
}

:deep(.menu .el-menu-item.is-active) {
  background: #e9f5f2;
  color: #0f766e;
  font-weight: 700;
}
</style>
