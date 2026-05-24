<template>
  <AppShell>
    <div class="page-header">
      <div>
        <div class="page-title">配置总览</div>
        <div class="page-subtitle">查看缓存、存储、MQTT 与 MinIO 的当前运行配置</div>
      </div>
      <el-button type="primary" :loading="loading" @click="load">刷新</el-button>
    </div>
    <div class="grid">
      <el-card class="metric" shadow="never">
        <div class="metric-label">MQTT 去重 TTL</div>
        <div class="metric-value">{{ config.cache?.mqtt_dedup_ttl_seconds || 0 }}s</div>
      </el-card>
      <el-card class="metric" shadow="never">
        <div class="metric-label">设备在线 TTL</div>
        <div class="metric-value">{{ config.cache?.device_online_ttl_seconds || 0 }}s</div>
      </el-card>
      <el-card class="metric" shadow="never">
        <div class="metric-label">上传 URL 有效期</div>
        <div class="metric-value">{{ config.storage?.upload_url_expire_seconds || 0 }}s</div>
      </el-card>
    </div>
    <el-card shadow="never" style="margin-top: 16px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="Bucket">{{ config.storage?.bucket }}</el-descriptions-item>
        <el-descriptions-item label="对象 Key 表达式">{{ config.storage?.object_key_pattern }}</el-descriptions-item>
        <el-descriptions-item label="MQTT Broker">{{ config.mqtt?.broker_url }}</el-descriptions-item>
        <el-descriptions-item label="MQTT Client ID">{{ config.mqtt?.client_id }}</el-descriptions-item>
        <el-descriptions-item label="MinIO Endpoint">{{ config.minio?.endpoint }}</el-descriptions-item>
        <el-descriptions-item label="MinIO AccessKey">{{ config.minio?.access_key }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </AppShell>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AppShell from '../components/AppShell.vue'
import { getConfig } from '../services/api'

const config = ref({})
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    config.value = await getConfig()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
