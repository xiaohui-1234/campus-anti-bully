<template>
  <AppShell>
    <div class="page-header">
      <div>
        <div class="page-title">MQTT 配置</div>
        <div class="page-subtitle">连接信息只读展示，修改 broker、账号等基础设施参数后需重启服务</div>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>
    <el-card shadow="never">
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
