<template>
  <AppShell>
    <div class="page-header">
      <div>
        <div class="page-title">设备管理</div>
        <div class="page-subtitle">查看和登记接入校园防霸凌系统的设备</div>
      </div>
      <div class="header-actions">
        <el-tooltip content="刷新设备列表" placement="bottom">
          <el-button :icon="Refresh" :loading="loading" circle @click="load" />
        </el-tooltip>
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">新增设备</el-button>
      </div>
    </div>

    <el-card class="device-panel" shadow="never">
      <div class="panel-top">
        <div class="summary-list">
          <div class="summary-item">
            <span class="summary-label">设备总数</span>
            <strong>{{ total }}</strong>
          </div>
          <div class="summary-divider" />
          <div class="summary-item">
            <span class="summary-label">本页在线</span>
            <strong class="online-number">{{ onlineCount }}</strong>
          </div>
          <div class="summary-divider" />
          <div class="summary-item">
            <span class="summary-label">本页设备</span>
            <strong>{{ records.length }}</strong>
          </div>
        </div>

        <div class="toolbar">
          <el-input
            v-model="keyword"
            clearable
            placeholder="搜索设备 ID 或产品类型"
            :prefix-icon="Search"
            @keyup.enter="search"
            @clear="search"
          />
          <el-button type="primary" plain :loading="loading" @click="search">查询</el-button>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="records"
        row-key="device_id"
        class="device-table"
        height="100%"
        empty-text="暂无设备"
      >
        <el-table-column label="设备" min-width="250">
          <template #default="{ row }">
            <div class="device-cell">
              <div class="device-icon"><el-icon><Cpu /></el-icon></div>
              <div class="device-identity">
                <strong>{{ row.device_id }}</strong>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="产品类型" min-width="180">
          <template #default="{ row }">
            <el-tag type="info" effect="plain">{{ row.product_type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="在线状态" width="120">
          <template #default="{ row }">
            <span class="status" :class="{ online: row.online_status === 'ONLINE' }">
              <i />
              {{ row.online_status === 'ONLINE' ? '在线' : '离线' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="last_online_time" label="最后在线时间" min-width="180">
          <template #default="{ row }">
            <span :class="{ muted: !row.last_online_time }">{{ row.last_online_time || '暂无记录' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="create_time" label="创建时间" min-width="180">
          <template #default="{ row }">{{ row.create_time || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right" align="center">
          <template #default="{ row }">
            <el-tooltip content="删除设备" placement="top">
              <el-button
                type="danger"
                plain
                circle
                :icon="Delete"
                :loading="deletingDeviceId === row.device_id"
                @click="confirmDelete(row)"
              />
            </el-tooltip>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-state">
            <div class="empty-icon"><el-icon><Cpu /></el-icon></div>
            <strong>{{ keyword ? '没有匹配的设备' : '暂未登记设备' }}</strong>
            <span>{{ keyword ? '请尝试调整搜索关键词' : '登记设备后即可在这里查看在线状态' }}</span>
            <el-button v-if="!keyword" type="primary" :icon="Plus" @click="openCreateDialog">新增设备</el-button>
          </div>
        </template>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          background
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          @current-change="load"
          @size-change="changeSize"
        />
      </div>
    </el-card>

    <el-dialog v-model="createDialogVisible" title="新增设备" width="560px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="dialog-intro">
          <div class="dialog-icon"><el-icon><Cpu /></el-icon></div>
          <div>
            <strong>登记新设备</strong>
            <span>设备 ID 登记后不可与已有设备重复</span>
          </div>
        </div>
        <div class="form-grid">
          <el-form-item label="设备 ID" prop="device_id">
            <el-input v-model="form.device_id" maxlength="64" placeholder="例如 dev001" />
          </el-form-item>
          <el-form-item label="产品类型" prop="product_type">
            <el-input v-model="form.product_type" maxlength="64" placeholder="例如 anti_bullying" />
          </el-form-item>
        </div>
        <el-form-item label="设备密钥" prop="device_secret">
          <el-input
            v-model="form.device_secret"
            type="password"
            maxlength="255"
            show-password
            autocomplete="new-password"
            placeholder="设备接入时使用的密钥"
          />
        </el-form-item>
        <div class="secret-tip">
          <el-icon><Lock /></el-icon>
          <span>设备密钥提交后只保存哈希值，后台不会再次显示明文。</span>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" :disabled="creating" @click="submitCreate">确认新增</el-button>
      </template>
    </el-dialog>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Cpu, Delete, Lock, Plus, Refresh, Search } from '@element-plus/icons-vue'
import AppShell from '../components/AppShell.vue'
import { createDevice, deleteDevice, getDevices } from '../services/api'

const loading = ref(false)
const creating = ref(false)
const deletingDeviceId = ref('')
const createDialogVisible = ref(false)
const formRef = ref()
const keyword = ref('')
const records = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
const onlineCount = computed(() => records.value.filter((item) => item.online_status === 'ONLINE').length)
const form = reactive(blankForm())
const identifierPattern = /^[A-Za-z0-9_-]+$/
const rules = {
  device_id: [
    { required: true, message: '请输入设备 ID', trigger: 'blur' },
    { pattern: identifierPattern, message: '只能包含字母、数字、下划线和短横线', trigger: ['blur', 'change'] }
  ],
  product_type: [
    { required: true, message: '请输入产品类型', trigger: 'blur' },
    { pattern: identifierPattern, message: '只能包含字母、数字、下划线和短横线', trigger: ['blur', 'change'] }
  ],
  device_secret: [{ required: true, message: '请输入设备密钥', trigger: 'blur' }]
}

function blankForm() {
  return {
    device_id: '',
    product_type: '',
    device_secret: ''
  }
}

async function load() {
  loading.value = true
  try {
    const result = await getDevices({
      keyword: keyword.value.trim() || undefined,
      page: page.value,
      size: size.value
    })
    records.value = result.records || []
    total.value = result.total || 0
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function changeSize() {
  page.value = 1
  load()
}

function openCreateDialog() {
  createDialogVisible.value = true
}

function resetForm() {
  Object.assign(form, blankForm())
  formRef.value?.clearValidate()
}

async function submitCreate() {
  if (!await formRef.value?.validate().catch(() => false)) return
  creating.value = true
  try {
    await createDevice({
      device_id: form.device_id.trim(),
      product_type: form.product_type.trim(),
      device_secret: form.device_secret
    })
    ElMessage.success('设备新增成功')
    createDialogVisible.value = false
    page.value = 1
    await load()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    creating.value = false
  }
}

async function confirmDelete(device) {
  try {
    await ElMessageBox.confirm(
      `确认删除设备“${device.device_id}”吗？存在用户绑定、事件或 WiFi 配置时将无法删除。`,
      '删除设备',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }

  deletingDeviceId.value = device.device_id
  try {
    await deleteDevice(device.device_id)
    ElMessage.success('设备删除成功')
    if (records.value.length === 1 && page.value > 1) {
      page.value -= 1
    }
    await load()
  } catch (err) {
    ElMessage.error(err.message)
  } finally {
    deletingDeviceId.value = ''
  }
}

onMounted(load)
</script>

<style scoped>
.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.device-panel {
  height: calc(100vh - 158px);
  min-height: 560px;
  border-radius: 8px;
}

.device-panel :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 20px 20px 0;
}

.panel-top {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding-bottom: 18px;
  border-bottom: 1px solid #eef0f4;
}

.summary-list {
  display: flex;
  align-items: center;
  min-height: 40px;
  gap: 22px;
}

.summary-item {
  display: grid;
  gap: 3px;
}

.summary-label {
  color: #667085;
  font-size: 12px;
}

.summary-item strong {
  color: #111827;
  font-size: 19px;
  line-height: 1.2;
}

.summary-item .online-number {
  color: #16855b;
}

.summary-divider {
  width: 1px;
  height: 30px;
  background: #e5e7eb;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.toolbar .el-input {
  width: 320px;
}

.device-table {
  flex: 1 1 auto;
  min-height: 0;
  width: 100%;
  margin-top: 8px;
}

.device-cell,
.dialog-intro,
.secret-tip,
.status {
  display: flex;
  align-items: center;
}

.device-cell {
  gap: 11px;
}

.device-icon,
.dialog-icon,
.empty-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  color: #0f766e;
  background: #e8f5f2;
}

.device-icon {
  width: 34px;
  height: 34px;
  border-radius: 6px;
}

.device-identity {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.device-identity strong {
  overflow: hidden;
  color: #1f2937;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.muted {
  color: #98a2b3;
  font-size: 12px;
}

.status {
  width: fit-content;
  gap: 7px;
  color: #667085;
  font-size: 13px;
  font-weight: 600;
}

.status i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #98a2b3;
}

.status.online {
  color: #16855b;
}

.status.online i {
  background: #22a06b;
  box-shadow: 0 0 0 4px #e8f7f0;
}

.empty-state {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 44px 0;
}

.empty-icon {
  width: 48px;
  height: 48px;
  margin-bottom: 4px;
  border-radius: 8px;
  font-size: 22px;
}

.empty-state strong {
  color: #344054;
  font-size: 15px;
}

.empty-state span {
  margin-bottom: 8px;
  color: #98a2b3;
  font-size: 13px;
}

.pagination {
  flex: 0 0 auto;
  display: flex;
  justify-content: flex-end;
  min-height: 72px;
  padding: 18px 0;
  border-top: 1px solid #eef0f4;
}

.dialog-intro {
  gap: 12px;
  margin: -2px 0 20px;
  padding: 12px;
  border: 1px solid #e4eeee;
  border-radius: 8px;
  background: #f7fbfa;
}

.dialog-icon {
  width: 38px;
  height: 38px;
  border-radius: 7px;
  font-size: 18px;
}

.dialog-intro > div:last-child {
  display: grid;
  gap: 4px;
}

.dialog-intro strong {
  color: #1f2937;
  font-size: 14px;
}

.dialog-intro span {
  color: #667085;
  font-size: 12px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.secret-tip {
  gap: 7px;
  margin-top: -5px;
  padding: 10px 12px;
  border-radius: 6px;
  background: #f6f8fa;
  color: #667085;
  font-size: 12px;
  line-height: 1.6;
}

:deep(.device-table .el-table__cell) {
  padding: 13px 0;
}

:deep(.device-table th.el-table__cell) {
  color: #667085;
  font-size: 12px;
  font-weight: 600;
  background: #fafbfc;
}

:deep(.device-table .el-scrollbar__bar.is-vertical) {
  width: 8px;
}

:deep(.device-table .el-scrollbar__thumb) {
  background: #c7d2d0;
}
</style>
