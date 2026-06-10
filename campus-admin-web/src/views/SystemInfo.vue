<template>
  <AppShell>
    <div class="page-header">
      <div>
        <div class="page-title">系统信息</div>
        <div class="page-subtitle">当前运行环境与核心业务对象数量</div>
      </div>
      <el-tooltip content="刷新系统信息" placement="bottom">
        <el-button :icon="Refresh" :loading="loading" circle @click="load" />
      </el-tooltip>
    </div>
    <div class="grid">
      <el-card class="metric" shadow="never">
        <div class="metric-icon"><el-icon><User /></el-icon></div>
        <div class="metric-label">用户数</div>
        <div class="metric-value">{{ info.user_count || 0 }}</div>
      </el-card>
      <el-card class="metric" shadow="never">
        <div class="metric-icon"><el-icon><Cpu /></el-icon></div>
        <div class="metric-label">设备数</div>
        <div class="metric-value">{{ info.device_count || 0 }}</div>
      </el-card>
      <el-card class="metric" shadow="never">
        <div class="metric-icon"><el-icon><Bell /></el-icon></div>
        <div class="metric-label">事件数</div>
        <div class="metric-value">{{ info.event_count || 0 }}</div>
      </el-card>
    </div>
    <el-card class="section-card environment-card" shadow="never">
      <template #header>
        <div class="card-heading">
          <div>
            <strong>运行环境</strong>
            <span>服务进程当前使用的基础环境信息</span>
          </div>
        </div>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="应用">{{ info.application_name }}</el-descriptions-item>
        <el-descriptions-item label="Java">{{ info.java_version }}</el-descriptions-item>
        <el-descriptions-item label="操作系统">{{ info.os_name }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </AppShell>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Bell, Cpu, Refresh, User } from '@element-plus/icons-vue'
import AppShell from '../components/AppShell.vue'
import { getSystemInfo } from '../services/api'

const loading = ref(false)
const info = ref({})

async function load() {
  loading.value = true
  try {
    info.value = await getSystemInfo()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.environment-card {
  margin-top: 18px;
}
</style>
