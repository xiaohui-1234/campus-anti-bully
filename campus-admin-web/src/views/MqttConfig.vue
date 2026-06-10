<template>
  <AppShell>
    <div class="page-header">
      <div>
        <div class="page-title">MQTT 配置</div>
        <div class="page-subtitle">连接信息只读展示，修改 broker、账号等基础设施参数后需重启服务</div>
      </div>
      <el-tooltip content="刷新 MQTT 配置" placement="bottom">
        <el-button :icon="Refresh" :loading="loading" circle @click="load" />
      </el-tooltip>
    </div>
    <el-card class="mqtt-status" shadow="never">
      <div class="status-icon"><el-icon><Connection /></el-icon></div>
      <div>
        <strong>消息服务</strong>
        <span>{{ config.enabled ? 'MQTT 服务已启用' : 'MQTT 服务当前未启用' }}</span>
      </div>
      <el-tag :type="config.enabled ? 'success' : 'info'" effect="plain">{{ config.enabled ? '运行中' : '未启用' }}</el-tag>
    </el-card>
    <el-card class="section-card mqtt-details" shadow="never">
      <template #header>
        <div class="card-heading">
          <div>
            <strong>连接参数</strong>
            <span>后端服务当前使用的 MQTT 基础连接信息</span>
          </div>
        </div>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="启用状态">
          <el-tag :type="config.enabled ? 'success' : 'info'">{{ config.enabled ? '启用' : '未启用' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="QoS">{{ config.qos }}</el-descriptions-item>
        <el-descriptions-item label="Broker">{{ config.broker_url }}</el-descriptions-item>
        <el-descriptions-item label="Client ID">{{ config.client_id }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </AppShell>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Connection, Refresh } from '@element-plus/icons-vue'
import AppShell from '../components/AppShell.vue'
import { getConfig } from '../services/api'

const loading = ref(false)
const config = ref({})

async function load() {
  loading.value = true
  try {
    const data = await getConfig()
    config.value = data.mqtt || {}
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.mqtt-status {
  max-width: 760px;
}

.mqtt-status :deep(.el-card__body) {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
}

.status-icon {
  width: 42px;
  height: 42px;
  border-radius: 8px;
  background: #e9f5f2;
  color: #0f766e;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.mqtt-status :deep(.el-card__body) > div:nth-child(2) {
  display: grid;
  gap: 4px;
}

.mqtt-status strong {
  color: #344054;
  font-size: 15px;
}

.mqtt-status span {
  color: #667085;
  font-size: 12px;
}

.mqtt-details {
  max-width: 920px;
  margin-top: 18px;
}
</style>
