<template>
  <el-container class="shell">
    <el-aside width="248px" class="aside">
      <div class="brand">
        <div class="brand-mark">防</div>
        <div>
          <div class="brand-title">校园防霸凌</div>
          <div class="brand-subtitle">配置后台</div>
        </div>
      </div>
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
        <el-menu-item index="/system">
          <el-icon><Monitor /></el-icon>
          <span>系统信息</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div>ADMIN 运维入口</div>
        <el-button :icon="SwitchButton" @click="logout">退出</el-button>
      </el-header>
      <el-main class="main">
        <slot />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { Connection, FolderOpened, Monitor, Operation, Setting, SwitchButton, Timer } from '@element-plus/icons-vue'

const router = useRouter()

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
  background: #ffffff;
  border-right: 1px solid #e5e7eb;
}

.brand {
  height: 72px;
  padding: 16px 18px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid #eef0f4;
}

.brand-mark {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: #0f766e;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
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
  border-right: 0;
}

.header {
  height: 64px;
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #475467;
}

.main {
  padding: 24px;
}
</style>
