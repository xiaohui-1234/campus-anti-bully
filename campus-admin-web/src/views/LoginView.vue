<template>
  <div class="login-page">
    <main class="login-shell">
      <section class="brand-panel">
        <div class="brand-mark">防</div>
        <div>
          <div class="brand-kicker">Campus Safety Console</div>
          <h1>校园防霸凌配置后台</h1>
          <p>用于查看系统运行状态、调整缓存与存储参数，并维护设备告警链路配置。</p>
        </div>
        <div class="guard-list">
          <div class="guard-item">
            <el-icon><UserFilled /></el-icon>
            <span>仅允许 ADMIN 用户通过 openid 登录</span>
          </div>
          <div class="guard-item">
            <el-icon><Key /></el-icon>
            <span>登录后自动签发 access token 与 refresh token</span>
          </div>
          <div class="guard-item">
            <el-icon><Lock /></el-icon>
            <span>后台接口统一走 Bearer Token 鉴权</span>
          </div>
        </div>
      </section>

      <section class="login-card">
        <div class="form-head">
          <div>
            <div class="form-title">管理员登录</div>
            <div class="form-subtitle">输入已登记的管理员 openid</div>
          </div>
          <div class="status-pill">ADMIN</div>
        </div>

        <label class="field-label">OpenID</label>
        <el-input
          v-model="openid"
          size="large"
          placeholder="请输入管理员 openid"
          clearable
          :prefix-icon="Key"
          @keyup.enter="submit"
        />
        <div class="field-help">openid 只用于本次登录校验，服务端按哈希匹配管理员账号。</div>

        <el-button type="primary" class="login-button" size="large" :loading="loading" @click="submit">
          <span>登录并进入后台</span>
        </el-button>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Key, Lock, UserFilled } from '@element-plus/icons-vue'
import { loginByOpenid } from '../services/api'

const openid = ref('')
const loading = ref(false)
const router = useRouter()

async function submit() {
  const value = openid.value.trim()
  if (!value) {
    ElMessage.warning('请输入 openid')
    return
  }
  loading.value = true
  try {
    await loginByOpenid(value)
    ElMessage.success('登录成功')
    router.push('/config')
  } catch (error) {
    ElMessage.error(error.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  background: #f4f7f7;
  color: #17212b;
  box-sizing: border-box;
  padding: 56px 24px;
}

.login-shell {
  width: min(960px, 100%);
  min-height: calc(100vh - 112px);
  margin: 0 auto;
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) 420px;
  align-items: center;
  gap: 36px;
}

.brand-panel {
  min-height: 440px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 24px 0;
}

.brand-mark {
  width: 58px;
  height: 58px;
  border-radius: 8px;
  background: #0f8b8d;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  font-weight: 800;
  box-shadow: 0 14px 30px rgba(15, 139, 141, 0.18);
}

.brand-kicker {
  margin-top: 28px;
  color: #0f8b8d;
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0;
}

h1 {
  margin: 12px 0 0;
  color: #111827;
  font-size: 38px;
  line-height: 1.18;
}

p {
  max-width: 520px;
  margin: 18px 0 0;
  color: #5f6f7a;
  font-size: 16px;
  line-height: 1.8;
}

.guard-list {
  margin-top: 42px;
  display: grid;
  gap: 14px;
}

.guard-item {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #3c4b55;
  font-size: 14px;
}

.guard-item .el-icon {
  width: 30px;
  height: 30px;
  border-radius: 7px;
  background: #e6f3f1;
  color: #0f766e;
  flex: 0 0 auto;
}

.login-card {
  background: #ffffff;
  border: 1px solid #e3e9ee;
  border-radius: 8px;
  padding: 34px;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.09);
  box-sizing: border-box;
}

.form-head {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: flex-start;
  margin-bottom: 30px;
}

.form-title {
  color: #111827;
  font-size: 24px;
  font-weight: 800;
}

.form-subtitle {
  margin-top: 7px;
  color: #687782;
  font-size: 14px;
}

.status-pill {
  height: 30px;
  padding: 0 12px;
  border-radius: 8px;
  background: #e8f4f3;
  color: #0f8b8d;
  display: flex;
  align-items: center;
  font-size: 12px;
  font-weight: 800;
}

.field-label {
  display: block;
  margin-bottom: 10px;
  color: #2c3a43;
  font-size: 14px;
  font-weight: 700;
}

.field-help {
  margin-top: 10px;
  color: #7a8993;
  font-size: 13px;
  line-height: 1.6;
}

.login-button {
  width: 100%;
  margin-top: 28px;
  font-weight: 700;
  position: relative;
  overflow: hidden;
  border: 0;
  background: #0f766e;
  box-shadow: 0 14px 28px rgba(15, 139, 141, 0.24);
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease,
    filter 0.18s ease;
}

.login-button:hover {
  transform: translateY(-2px);
  background: #0d6d65;
  box-shadow: 0 18px 36px rgba(15, 118, 110, 0.25);
}

.login-button:active {
  transform: translateY(0);
  box-shadow: 0 10px 22px rgba(15, 139, 141, 0.22);
}

.login-button span {
  position: relative;
  z-index: 1;
}

.login-button.is-loading {
  transform: none;
  box-shadow: 0 12px 24px rgba(15, 139, 141, 0.18);
}

@media (max-width: 820px) {
  .login-page {
    padding: 28px 18px;
  }

  .login-shell {
    min-height: calc(100vh - 56px);
    grid-template-columns: 1fr;
    gap: 22px;
  }

  .brand-panel {
    min-height: auto;
  }

  h1 {
    font-size: 30px;
  }

  .guard-list {
    margin-top: 26px;
  }

  .login-card {
    padding: 26px;
  }
}
</style>
