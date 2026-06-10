<template>
  <AppShell>
    <div class="page-header">
      <div>
        <div class="page-title">存储配置</div>
        <div class="page-subtitle">对象 Key 表达式与临时 URL 有效期；敏感密钥由后端脱敏</div>
      </div>
      <el-button type="primary" :icon="Check" :loading="saving" @click="save">保存配置</el-button>
    </div>
    <el-card class="form-card" shadow="never">
      <template #header>
        <div class="card-heading">
          <div>
            <strong>对象存储规则</strong>
            <span>配置上传路径和临时访问凭证的有效时间</span>
          </div>
          <el-icon class="heading-icon"><FolderOpened /></el-icon>
        </div>
      </template>
      <el-form class="config-form" :model="form" label-width="180px">
        <el-form-item label="Bucket">
          <el-input v-model="bucket" disabled />
        </el-form-item>
        <el-form-item label="对象 Key 表达式">
          <el-input v-model="form.object_key_pattern" />
          <div class="field-note">支持使用产品类型、设备 ID、日期和事件 ID 等占位字段。</div>
        </el-form-item>
        <el-form-item label="上传 URL 有效期">
          <el-input-number v-model="form.upload_url_expire_seconds" :min="60" />
          <span class="field-unit">秒</span>
        </el-form-item>
        <el-form-item label="访问 URL 有效期">
          <el-input-number v-model="form.access_url_expire_seconds" :min="60" />
          <span class="field-unit">秒</span>
        </el-form-item>
      </el-form>
    </el-card>
  </AppShell>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, FolderOpened } from '@element-plus/icons-vue'
import AppShell from '../components/AppShell.vue'
import { getConfig, updateConfig } from '../services/api'

const saving = ref(false)
const bucket = ref('')
const form = reactive({
  object_key_pattern: '',
  upload_url_expire_seconds: 300,
  access_url_expire_seconds: 3600
})

async function load() {
  const data = await getConfig()
  bucket.value = data.storage?.bucket || ''
  Object.assign(form, data.storage || {})
}

async function save() {
  saving.value = true
  try {
    await updateConfig({ storage: form })
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

:deep(.config-form .el-input) {
  max-width: 520px;
}
</style>
