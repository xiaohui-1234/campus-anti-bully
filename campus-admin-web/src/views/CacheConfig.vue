<template>
  <AppShell>
    <div class="page-header">
      <div>
        <div class="page-title">缓存配置</div>
        <div class="page-subtitle">Redis TTL 运行参数，提交后立即影响后续业务使用</div>
      </div>
      <el-button type="primary" :icon="Check" :loading="saving" @click="save">保存配置</el-button>
    </div>
    <el-card class="form-card" shadow="never">
      <template #header>
        <div class="card-heading">
          <div>
            <strong>过期时间设置</strong>
            <span>单位统一为秒，调整后影响新写入的缓存数据</span>
          </div>
          <el-icon class="heading-icon"><Timer /></el-icon>
        </div>
      </template>
      <el-form class="config-form" :model="form" label-width="180px">
        <el-form-item label="MQTT 去重 TTL">
          <el-input-number v-model="form.mqtt_dedup_ttl_seconds" :min="60" />
          <span class="field-unit">秒</span>
        </el-form-item>
        <el-form-item label="设备在线 TTL">
          <el-input-number v-model="form.device_online_ttl_seconds" :min="10" />
          <span class="field-unit">秒</span>
        </el-form-item>
        <el-form-item label="绑定码 TTL">
          <el-input-number v-model="form.bind_code_ttl_seconds" :min="10" />
          <span class="field-unit">秒</span>
        </el-form-item>
        <el-form-item label="访问 URL TTL">
          <el-input-number v-model="form.access_url_ttl_seconds" :min="60" />
          <span class="field-unit">秒</span>
        </el-form-item>
      </el-form>
    </el-card>
  </AppShell>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Timer } from '@element-plus/icons-vue'
import AppShell from '../components/AppShell.vue'
import { getConfig, updateConfig } from '../services/api'

const saving = ref(false)
const form = reactive({
  mqtt_dedup_ttl_seconds: 86400,
  device_online_ttl_seconds: 90,
  bind_code_ttl_seconds: 60,
  access_url_ttl_seconds: 3600
})

async function load() {
  const data = await getConfig()
  Object.assign(form, data.cache || {})
}

async function save() {
  saving.value = true
  try {
    await updateConfig({ cache: form })
    ElMessage.success('已保存')
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.heading-icon {
  color: #0f766e;
  font-size: 20px;
}
</style>
