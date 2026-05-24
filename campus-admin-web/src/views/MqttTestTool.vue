<template>
  <AppShell>
    <div v-if="!activeDevice" class="device-page">
      <div class="page-header">
        <div>
          <div class="page-title">硬件测试工具</div>
          <div class="page-subtitle">纯前端设备列表。每台设备独立保存 MQTT 参数、遗嘱和任务。</div>
        </div>
        <el-button type="primary" @click="openDevice()">添加设备</el-button>
      </div>

      <div class="device-grid">
        <el-card v-for="device in devices" :key="device.id" shadow="never" class="device-card" @click="enterDevice(device)">
          <div class="device-head">
            <div>
              <div class="device-name">{{ device.name || device.device_id }}</div>
              <div class="device-id">{{ device.product_type }} / {{ device.device_id }}</div>
            </div>
            <el-tag :type="runtime[device.id]?.connected ? 'success' : 'info'">
              {{ runtime[device.id]?.connected ? '已连接' : '未连接' }}
            </el-tag>
          </div>
          <div class="device-meta">
            <span>{{ device.broker_url || '未配置 Broker' }}</span>
            <span>{{ device.tasks.length }} 个任务</span>
          </div>
          <div class="device-actions" @click.stop>
            <el-button size="small" @click="openDevice(device)">参数</el-button>
            <el-button size="small" type="danger" plain @click="removeDevice(device)">删除</el-button>
          </div>
        </el-card>
        <el-empty v-if="!devices.length" description="还没有模拟设备，先添加一台" />
      </div>
    </div>

    <div v-else class="detail-page">
      <div class="page-header">
        <div>
          <div class="page-title">{{ activeDevice.name || activeDevice.device_id }}</div>
          <div class="page-subtitle">{{ activeDevice.product_type }} / {{ activeDevice.device_id }}</div>
        </div>
        <div class="head-actions">
          <el-tag :type="currentRuntime.connected ? 'success' : 'info'">{{ currentRuntime.connected ? '已连接' : '未连接' }}</el-tag>
          <el-button v-if="!currentRuntime.connected" type="primary" :loading="currentRuntime.connecting" @click="connectDevice(activeDevice)">连接</el-button>
          <el-button v-else @click="disconnectDevice(activeDevice)">断开</el-button>
          <el-button @click="activeDevice = null">返回列表</el-button>
        </div>
      </div>

      <div class="detail-layout">
        <el-card shadow="never" class="side-card">
          <template #header>
            <div class="card-head">
              <span>设备参数</span>
              <div class="head-actions">
                <el-button size="small" @click="openSubscribeDialog">订阅</el-button>
                <el-button size="small" @click="openDevice(activeDevice)">编辑</el-button>
              </div>
            </div>
          </template>
          <div class="summary-list">
            <div class="summary-item">
              <span>Broker</span>
              <strong>{{ activeDevice.broker_url }}</strong>
            </div>
            <div class="summary-item">
              <span>Client ID</span>
              <strong>{{ effectiveClientId(activeDevice) }}</strong>
            </div>
            <div class="summary-item">
              <span>Username</span>
              <strong>{{ effectiveUsername(activeDevice) }}</strong>
            </div>
            <div class="summary-item">
              <span>订阅</span>
              <strong>{{ activeDevice.subscriptions.length }} 个 Topic</strong>
            </div>
            <div class="summary-item">
              <span>遗嘱</span>
              <strong>{{ activeDevice.will_enabled ? '已启用' : '未启用' }}</strong>
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="main-card">
          <template #header>
            <div class="card-head">
              <span>任务列表</span>
              <el-button type="primary" size="small" @click="openTask()">添加任务</el-button>
            </div>
          </template>
          <div class="task-list">
            <div v-for="task in activeDevice.tasks" :key="task.id" class="task-row">
              <div>
                <div class="task-name">{{ task.name }}</div>
                <div class="task-sub">{{ topicOf(activeDevice, task) }} / {{ task.interval_seconds }}s</div>
              </div>
              <div class="task-actions">
                <el-tag :type="task.running ? 'success' : 'info'">{{ task.running ? '运行中' : '已停止' }}</el-tag>
                <el-button size="small" :disabled="!currentRuntime.connected" @click="sendTask(activeDevice, task)">发送</el-button>
                <el-button v-if="!task.running" size="small" type="primary" :disabled="!currentRuntime.connected" @click="startTask(activeDevice, task)">定时</el-button>
                <el-button v-else size="small" @click="stopTask(task)">停止</el-button>
                <el-button size="small" @click="openTask(task)">编辑</el-button>
                <el-button size="small" type="danger" plain @click="removeTask(task)">删除</el-button>
              </div>
            </div>
            <el-empty v-if="!activeDevice.tasks.length" description="这台设备还没有任务" />
          </div>
        </el-card>

        <el-card shadow="never" class="log-card">
          <template #header>
            <div class="card-head">
              <span>收发日志</span>
              <el-button size="small" @click="currentRuntime.logs = []">清空</el-button>
            </div>
          </template>
          <div class="logs">
            <div v-for="(item, index) in currentRuntime.logs" :key="index" class="log-item">
              <div class="log-head">
                <el-tag size="small" :type="item.type === 'IN' ? 'success' : item.type === 'OUT' ? 'primary' : 'danger'">{{ item.type }}</el-tag>
                <span>{{ item.topic }}</span>
                <em>{{ item.time }}</em>
              </div>
              <pre>{{ item.payload }}</pre>
            </div>
            <el-empty v-if="!currentRuntime.logs.length" description="暂无日志" />
          </div>
        </el-card>
      </div>
    </div>

    <el-dialog v-model="deviceDialog" :title="deviceForm.id ? '编辑设备' : '添加设备'" width="720px">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="设备名称"><el-input v-model="deviceForm.name" /></el-form-item>
          <el-form-item label="Product Type"><el-input v-model="deviceForm.product_type" /></el-form-item>
          <el-form-item label="Device ID"><el-input v-model="deviceForm.device_id" /></el-form-item>
        </div>
        <el-form-item label="Broker WebSocket URL"><el-input v-model="deviceForm.broker_url" placeholder="ws://127.0.0.1:8083/mqtt" /></el-form-item>
        <div class="form-grid">
          <el-form-item label="Client ID">
            <el-input v-model="deviceForm.client_id" placeholder="默认自动使用 device_id" />
          </el-form-item>
          <el-form-item label="Username">
            <el-input v-model="deviceForm.username" placeholder="默认自动使用 Product Type" />
          </el-form-item>
          <el-form-item label="Password"><el-input v-model="deviceForm.password" type="password" show-password /></el-form-item>
        </div>
        <div class="form-tip">Client ID 留空时使用 Device ID；Username 留空时使用 Product Type。密码需填写 Broker/EMQX 为该模拟设备配置的 MQTT 密码。</div>
        <el-divider content-position="left">遗嘱</el-divider>
        <el-checkbox v-model="deviceForm.will_enabled">启用遗嘱消息</el-checkbox>
        <el-button class="will-template-button" size="small" @click="fillWillTemplate">使用项目默认遗嘱</el-button>
        <div class="form-grid will-grid">
          <el-form-item label="遗嘱 Topic 后缀"><el-input v-model="deviceForm.will_action" placeholder="status/online" /></el-form-item>
          <el-form-item label="QoS"><el-input-number v-model="deviceForm.will_qos" :min="0" :max="2" /></el-form-item>
          <el-form-item label="Retain"><el-switch v-model="deviceForm.will_retain" /></el-form-item>
        </div>
        <div class="topic-preview">完整遗嘱 Topic：{{ willTopic(deviceForm) }}</div>
        <el-form-item label="Will Payload">
          <el-input v-model="deviceForm.will_payload" class="json-editor" type="textarea" :rows="5" spellcheck="false" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deviceDialog = false">取消</el-button>
        <el-button type="primary" @click="saveDevice">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="taskDialog" :title="taskForm.id ? '编辑任务' : '添加任务'" width="760px">
      <el-form label-position="top">
        <div class="form-grid task-form-grid">
          <el-form-item label="任务名"><el-input v-model="taskForm.name" /></el-form-item>
          <el-form-item label="模板">
            <el-select v-model="taskTemplate" @change="applyTemplate">
              <el-option label="心跳" value="heartbeat" />
              <el-option label="绑定码上报" value="bind" />
              <el-option label="报警上报" value="alarm" />
              <el-option label="上传确认" value="confirm" />
              <el-option label="WiFi 列表" value="wifi" />
              <el-option label="WiFi 设置回复" value="wifiReply" />
            </el-select>
          </el-form-item>
          <el-form-item label="间隔秒"><el-input-number v-model="taskForm.interval_seconds" :min="1" /></el-form-item>
        </div>
        <div class="sync-row">
          <el-checkbox v-model="taskForm.sync_timestamp">发送时同步 timestamp 为当前时间</el-checkbox>
          <el-checkbox v-model="taskForm.sync_mqtt_msg_id">发送时按当前时间重生成 mqtt_msg_id</el-checkbox>
        </div>
        <el-form-item label="Action"><el-input v-model="taskForm.action" placeholder="status/online" /></el-form-item>
        <el-form-item label="Payload JSON">
          <el-input v-model="taskForm.payload" class="json-editor" type="textarea" :rows="10" spellcheck="false" />
        </el-form-item>
        <div class="form-tip">发送时工具会自动兜底补充 `mqtt_msg_id`、`product_type`、`device_id`；模板本身按后端 MQTT DTO 字段生成。</div>
      </el-form>
      <template #footer>
        <el-button @click="taskDialog = false">取消</el-button>
        <el-button type="primary" @click="saveTask">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="subscribeDialog" title="订阅 Topic" width="820px">
      <div class="subscribe-toolbar">
        <el-button @click="addSubscribeTemplate('alarmUpload')">添加报警上传信息</el-button>
        <el-button @click="addSubscribeTemplate('wifiSet')">添加 WiFi 设置</el-button>
        <el-button type="primary" :disabled="!currentRuntime.connected" @click="subscribeAll(activeDevice)">订阅全部</el-button>
      </div>
      <div class="subscribe-list dialog-subscribe-list">
        <div v-for="item in activeDevice.subscriptions" :key="item.id" class="subscribe-row">
          <el-input v-model="item.name" placeholder="名称" />
          <el-input v-model="item.topic" placeholder="device/{product_type}/{device_id}/alarm/upload" />
          <el-button :disabled="!currentRuntime.connected" @click="subscribeTopic(activeDevice, item)">订阅</el-button>
          <el-button type="danger" plain @click="removeSubscribe(item)">删除</el-button>
        </div>
      </div>
    </el-dialog>
  </AppShell>
</template>

<script setup>
import mqtt from 'mqtt'
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AppShell from '../components/AppShell.vue'

const STORE_KEY = 'mqtt_device_lab'
const saved = JSON.parse(localStorage.getItem(STORE_KEY) || '{}')
const devices = ref((saved.devices || []).map(normalizeDevice))
const runtime = reactive({})
const activeDevice = ref(null)
const deviceDialog = ref(false)
const taskDialog = ref(false)
const subscribeDialog = ref(false)
const taskTemplate = ref('heartbeat')
const deviceForm = reactive(blankDevice())
const taskForm = reactive(blankTask())
const currentRuntime = computed(() => activeDevice.value ? ensureRuntime(activeDevice.value) : { connected: false, logs: [] })

function blankDevice() {
  return {
    id: '',
    name: '模拟设备',
    product_type: 'anti_bullying',
    device_id: 'dev001',
    broker_url: 'ws://127.0.0.1:8083/mqtt',
    client_id: '',
    username: '',
    password: '',
    will_enabled: false,
    will_action: 'status/online',
    will_qos: 1,
    will_retain: false,
    will_payload: JSON.stringify(defaultWillPayload('anti_bullying', 'dev001'), null, 2),
    subscriptions: defaultSubscriptions('anti_bullying', 'dev001'),
    tasks: []
  }
}

function blankTask(device = activeDevice.value) {
  const template = buildTaskTemplate('heartbeat', device)
  return {
    id: '',
    name: template.name,
    action: template.action,
    interval_seconds: 30,
    payload: JSON.stringify(template.payload, null, 2),
    sync_timestamp: true,
    sync_mqtt_msg_id: true,
    running: false,
    timer: null
  }
}

function normalizeDevice(device) {
  return {
    ...device,
    subscriptions: device.subscriptions || defaultSubscriptions(device.product_type, device.device_id),
    tasks: device.tasks || []
  }
}

function enterDevice(device) {
  activeDevice.value = device
  ensureRuntime(device)
}

function openDevice(device) {
  Object.assign(deviceForm, device ? clonePlain(device) : blankDevice())
  deviceDialog.value = true
}

function saveDevice() {
  if (!deviceForm.product_type || !deviceForm.device_id || !deviceForm.broker_url) {
    ElMessage.warning('请填写 product_type、device_id 和 Broker')
    return
  }
  const id = `${deviceForm.product_type}/${deviceForm.device_id}`
  const next = {
    ...clonePlain(deviceForm),
    id,
    subscriptions: deviceForm.subscriptions || defaultSubscriptions(deviceForm.product_type, deviceForm.device_id),
    tasks: deviceForm.tasks || []
  }
  const index = devices.value.findIndex((item) => item.id === id || item.id === deviceForm.id)
  if (index >= 0) devices.value[index] = next
  else devices.value.push(next)
  if (activeDevice.value && (activeDevice.value.id === deviceForm.id || activeDevice.value.id === id)) activeDevice.value = next
  deviceDialog.value = false
  saveLocal()
  ElMessage.success('设备已保存')
}

function fillWillTemplate() {
  deviceForm.will_action = 'status/online'
  deviceForm.will_payload = JSON.stringify(defaultWillPayload(deviceForm.product_type, deviceForm.device_id), null, 2)
}

function removeDevice(device) {
  disconnectDevice(device)
  devices.value = devices.value.filter((item) => item.id !== device.id)
  if (activeDevice.value?.id === device.id) activeDevice.value = null
  saveLocal()
}

function openTask(task) {
  taskTemplate.value = task ? taskTemplate.value : 'heartbeat'
  Object.assign(taskForm, task ? { ...task, payload: JSON.stringify(parsePayload(task.payload), null, 2) } : blankTask(activeDevice.value))
  taskDialog.value = true
}

function saveTask() {
  if (!activeDevice.value) return
  let payload
  try {
    payload = JSON.parse(taskForm.payload || '{}')
  } catch {
    ElMessage.error('Payload JSON 格式不正确')
    return
  }
  const next = {
    id: taskForm.id || `task-${Date.now()}`,
    name: taskForm.name,
    action: taskForm.action,
    interval_seconds: taskForm.interval_seconds,
    payload,
    sync_timestamp: taskForm.sync_timestamp,
    sync_mqtt_msg_id: taskForm.sync_mqtt_msg_id,
    running: false,
    timer: null
  }
  const index = activeDevice.value.tasks.findIndex((item) => item.id === next.id)
  if (index >= 0) activeDevice.value.tasks[index] = next
  else activeDevice.value.tasks.push(next)
  taskDialog.value = false
  saveLocal()
}

function removeTask(task) {
  stopTask(task)
  activeDevice.value.tasks = activeDevice.value.tasks.filter((item) => item.id !== task.id)
  saveLocal()
}

function connectDevice(device) {
  const state = ensureRuntime(device)
  if (state.connected || state.connecting) return
  state.connecting = true
  let will
  if (device.will_enabled) {
    will = {
      topic: willTopic(device),
      payload: normalizePayload(device, parsePayload(device.will_payload)),
      qos: device.will_qos,
      retain: device.will_retain
    }
  }
  state.client = mqtt.connect(device.broker_url, {
    clientId: device.client_id || device.device_id || `web-device-${Date.now()}`,
    username: effectiveUsername(device),
    password: device.password || undefined,
    clean: true,
    reconnectPeriod: 0,
    connectTimeout: 8000,
    will
  })
  state.client.on('connect', () => {
    state.connected = true
    state.connecting = false
    addLog(device, 'SYS', 'broker', '连接成功')
    subscribeDownlink(device)
  })
  state.client.on('message', (topic, payload) => addLog(device, 'IN', topic, payload.toString()))
  state.client.on('error', (error) => {
    addLog(device, 'ERR', 'broker', error.message)
    if (String(error.message || '').toLowerCase().includes('not authorized')) {
      ElMessage.error('MQTT 鉴权失败：请检查 Username / Password / EMQX WebSocket 认证配置')
      disconnectDevice(device)
    }
  })
  state.client.on('close', () => {
    state.connected = false
    state.connecting = false
    addLog(device, 'SYS', 'broker', '连接关闭')
  })
}

function disconnectDevice(device) {
  const state = ensureRuntime(device)
  device.tasks.forEach(stopTask)
  state.client?.end(true)
  state.client = null
  state.connected = false
  state.connecting = false
}

function subscribeDownlink(device) {
  subscribeAll(device)
}

function subscribeAll(device) {
  const items = device.subscriptions?.length ? device.subscriptions : defaultSubscriptions(device.product_type, device.device_id)
  items.forEach((item) => subscribeTopic(device, item))
}

function subscribeTopic(device, item) {
  const state = ensureRuntime(device)
  if (!state.client || !state.connected) {
    ElMessage.warning('请先连接设备')
    return
  }
  state.client.subscribe(item.topic, { qos: 1 }, (error) => {
    addLog(device, error ? 'ERR' : 'SYS', item.topic, error ? error.message : `已订阅：${item.name || item.topic}`)
  })
}

function addSubscribeTemplate(type) {
  if (!activeDevice.value) return
  const templates = subscriptionTemplates(activeDevice.value)
  activeDevice.value.subscriptions = activeDevice.value.subscriptions || []
  activeDevice.value.subscriptions.push({ id: `sub-${Date.now()}`, ...templates[type] })
  saveLocal()
}

function openSubscribeDialog() {
  subscribeDialog.value = true
}

function removeSubscribe(item) {
  if (!activeDevice.value) return
  activeDevice.value.subscriptions = activeDevice.value.subscriptions.filter((sub) => sub.id !== item.id)
  saveLocal()
}

function sendTask(device, task) {
  const state = ensureRuntime(device)
  if (!state.connected || !state.client) {
    ElMessage.warning('请先连接设备')
    return
  }
  const topic = topicOf(device, task)
  const payload = normalizePayload(device, prepareTaskPayload(device, task))
  state.client.publish(topic, payload, { qos: 1 }, (error) => {
    addLog(device, error ? 'ERR' : 'OUT', topic, error ? error.message : payload)
  })
}

function startTask(device, task) {
  stopTask(task)
  task.running = true
  task.timer = window.setInterval(() => sendTask(device, task), task.interval_seconds * 1000)
  sendTask(device, task)
}

function stopTask(task) {
  if (task.timer) window.clearInterval(task.timer)
  task.timer = null
  task.running = false
}

function applyTemplate() {
  const template = buildTaskTemplate(taskTemplate.value)
  Object.assign(taskForm, {
    name: template.name,
    action: template.action,
    payload: JSON.stringify(template.payload, null, 2)
  })
}

function buildTaskTemplate(type, device = activeDevice.value) {
  const now = Date.now()
  const msgId = `${now}-001-${device?.device_id || 'dev001'}`
  const productType = device?.product_type || 'anti_bullying'
  const deviceId = device?.device_id || 'dev001'
  const templates = {
    heartbeat: {
      name: '设备心跳',
      action: 'status/online',
      payload: {
        mqtt_msg_id: msgId,
        product_type: productType,
        device_id: deviceId,
        status: 'online',
        timestamp: now
      }
    },
    bind: {
      name: '绑定码上报',
      action: 'bind',
      payload: {
        mqtt_msg_id: msgId,
        product_type: productType,
        device_id: deviceId,
        bind_code: '123456'
      }
    },
    alarm: {
      name: '报警上报',
      action: 'alarm/post',
      payload: {
        mqtt_msg_id: msgId,
        product_type: productType,
        device_id: deviceId,
        event_type: 'SOS',
        alarm_info: '模拟报警',
        file_name: `alarm-${now}.wav`,
        file_size: 1024,
        content_type: 'audio/wav',
        timestamp: now
      }
    },
    confirm: {
      name: '上传确认',
      action: 'alarm/confirm',
      payload: {
        mqtt_msg_id: msgId,
        product_type: productType,
        device_id: deviceId,
        event_id: '替换为 alarm/upload 返回的 event_id',
        object_key: '替换为 alarm/upload 返回的 object_key',
        upload_status: 'success'
      }
    },
    wifi: {
      name: 'WiFi 列表',
      action: 'config/wifi/list',
      payload: {
        mqtt_msg_id: msgId,
        max_size: 5,
        id_using: 1,
        config: [
          {
            config_id: 1,
            wifi_name: 'Campus-WiFi'
          }
        ]
      }
    },
    wifiReply: {
      name: 'WiFi 设置回复',
      action: 'config/wifi/set/reply',
      payload: {
        mqtt_msg_id: msgId,
        config_id: 1,
        success: true
      }
    }
  }
  return templates[type]
}

function defaultWillPayload(productType, deviceId) {
  return {
    mqtt_msg_id: `${Date.now()}-will-${deviceId || 'device'}`,
    product_type: productType || 'anti_bullying',
    device_id: deviceId || 'dev001',
    status: 'offline',
    timestamp: Date.now()
  }
}

function defaultSubscriptions(productType, deviceId) {
  const templates = subscriptionTemplates({ product_type: productType, device_id: deviceId })
  return [
    { id: 'alarm-upload', ...templates.alarmUpload },
    { id: 'wifi-set', ...templates.wifiSet }
  ]
}

function subscriptionTemplates(device) {
  return {
    alarmUpload: {
      name: '报警上传信息',
      topic: `device/${device.product_type}/${device.device_id}/alarm/upload`
    },
    wifiSet: {
      name: 'WiFi 设置下发',
      topic: `device/${device.product_type}/${device.device_id}/config/wifi/set`
    }
  }
}

function ensureRuntime(device) {
  runtime[device.id] = runtime[device.id] || { client: null, connected: false, connecting: false, logs: [] }
  return runtime[device.id]
}

function topicOf(device, task) {
  return `device/${device.product_type}/${device.device_id}/${task.action}`
}

function willTopic(device) {
  return `device/${device.product_type}/${device.device_id}/${device.will_action}`
}

function effectiveClientId(device) {
  return device.client_id || device.device_id || `web-device-${Date.now()}`
}

function effectiveUsername(device) {
  return device.username || device.product_type || undefined
}

function normalizePayload(device, payload) {
  const body = parsePayload(payload)
  return JSON.stringify({
    ...body,
    mqtt_msg_id: body.mqtt_msg_id || `${Date.now()}-web-${device.device_id}`,
    product_type: device.product_type,
    device_id: device.device_id
  })
}

function prepareTaskPayload(device, task) {
  const body = parsePayload(task.payload)
  const now = Date.now()
  if (task.sync_timestamp) {
    body.timestamp = now
  }
  if (task.sync_mqtt_msg_id) {
    body.mqtt_msg_id = `${now}-001-${device.device_id}`
  }
  return body
}

function parsePayload(payload) {
  if (!payload) return {}
  if (typeof payload === 'string') return JSON.parse(payload)
  return payload
}

function addLog(device, type, topic, payload) {
  const state = ensureRuntime(device)
  state.logs.unshift({ type, topic, payload, time: new Date().toLocaleTimeString() })
  state.logs = state.logs.slice(0, 120)
}

function saveLocal() {
  const cleanDevices = devices.value.map((device) => ({
    ...device,
    subscriptions: device.subscriptions || defaultSubscriptions(device.product_type, device.device_id),
    tasks: device.tasks.map(({ timer, running, ...task }) => task)
  }))
  localStorage.setItem(STORE_KEY, JSON.stringify({ devices: cleanDevices }))
}

function clonePlain(value) {
  return JSON.parse(JSON.stringify(value))
}

onBeforeUnmount(() => {
  devices.value.forEach(disconnectDevice)
})
</script>

<style scoped>
.device-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.device-card {
  border-radius: 8px;
  cursor: pointer;
}

.device-card:hover {
  border-color: #0f8b8d;
}

.device-head,
.device-meta,
.device-actions,
.head-actions,
.card-head,
.task-row,
.task-actions,
.log-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.device-head,
.card-head,
.task-row,
.log-head {
  justify-content: space-between;
}

.device-name,
.task-name {
  color: #111827;
  font-weight: 800;
}

.device-id,
.task-sub {
  margin-top: 5px;
  color: #667085;
  font-size: 12px;
}

.device-meta {
  justify-content: space-between;
  margin-top: 18px;
  color: #667085;
  font-size: 12px;
}

.device-actions {
  justify-content: flex-end;
  margin-top: 18px;
}

.detail-layout {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  align-items: start;
  gap: 16px;
}

.side-card,
.main-card,
.log-card {
  border-radius: 8px;
}

.log-card {
  grid-column: 1 / -1;
}

.task-list {
  display: grid;
  gap: 12px;
}

.summary-list {
  display: grid;
  gap: 10px;
}

.summary-item {
  display: grid;
  grid-template-columns: 82px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  min-height: 42px;
  padding: 8px 10px;
  border: 1px solid #edf0f5;
  border-radius: 8px;
  background: #fbfcfe;
}

.summary-item span {
  color: #667085;
  font-size: 13px;
}

.summary-item strong {
  min-width: 0;
  overflow: hidden;
  color: #1f2937;
  font-size: 13px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.subscribe-toolbar {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.subscribe-list {
  display: grid;
  gap: 10px;
}

.dialog-subscribe-list {
  max-height: 420px;
  overflow: auto;
}

.subscribe-row {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr) auto auto;
  gap: 10px;
  align-items: center;
}

.task-row {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px;
}

.task-row > div:first-child {
  min-width: 0;
}

.task-sub {
  max-width: 680px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.logs {
  max-height: 360px;
  overflow: auto;
}

.log-item {
  border-bottom: 1px solid #eef0f4;
  padding: 10px 0;
}

.log-head span {
  color: #344054;
  font-size: 12px;
  font-weight: 700;
}

.log-head em {
  color: #98a2b3;
  font-size: 12px;
  font-style: normal;
}

pre {
  margin: 8px 0 0;
  color: #475467;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.form-tip {
  margin-top: -4px;
  color: #667085;
  font-size: 13px;
  line-height: 1.6;
}

.topic-preview {
  margin: -4px 0 14px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f6f8fa;
  color: #344054;
  font-size: 13px;
  word-break: break-all;
}

.sync-row {
  display: flex;
  align-items: center;
  gap: 22px;
  margin: 0 0 14px;
  flex-wrap: wrap;
}

.will-template-button {
  margin-left: 12px;
}

.task-form-grid {
  grid-template-columns: 1.4fr 1fr 0.8fr;
}

:deep(.json-editor textarea) {
  font-family: Consolas, Monaco, monospace;
  line-height: 1.55;
}

@media (max-width: 1100px) {
  .detail-layout,
  .form-grid,
  .task-form-grid,
  .subscribe-row {
    grid-template-columns: 1fr;
  }

  .log-card {
    grid-column: auto;
  }
}
</style>
