<template>
  <AppShell>
    <div class="page-header">
      <div>
        <div class="page-title">存储配置</div>
        <div class="page-subtitle">对象 Key 表达式与临时 URL 有效期；敏感密钥由后端脱敏</div>
      </div>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </div>
    <el-card class="form-card" shadow="never">
      <el-form :model="form" label-width="180px">
        <el-form-item label="Bucket">
          <el-input v-model="bucket" disabled />
        </el-form-item>
        <el-form-item label="对象 Key 表达式">
          <el-input v-model="form.object_key_pattern" />
        </el-form-item>
        <el-form-item label="上传 URL 有效期">
          <el-input-number v-model="form.upload_url_expire_seconds" :min="60" />
        </el-form-item>
        <el-form-item label="访问 URL 有效期">
          <el-input-number v-model="form.access_url_expire_seconds" :min="60" />
        </el-form-item>
      </el-form>
    </el-card>
  </AppShell>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
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
