<template>
  <div class="login-page">
    <section class="login-panel">
      <div class="panel-header">
        <span class="panel-chip">成员入口</span>
        <h2>登录你的星球账号</h2>
        <p class="panel-subtitle">请使用邮箱与密码进入你的知识工作台。</p>
      </div>

      <el-alert
        v-if="errorMessage"
        type="error"
        :closable="false"
        show-icon
        class="panel-alert"
        :title="errorMessage"
      />

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="login-form"
        @submit.prevent
      >
        <el-form-item label="邮箱" prop="email">
          <el-input
            v-model="form.email"
            placeholder="name@example.com"
            :prefix-icon="Message"
            autocomplete="username"
            clearable
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>

        <div class="form-meta">
          <el-checkbox v-model="form.remember">记住邮箱</el-checkbox>
        </div>

        <el-button
          type="primary"
          class="login-button"
          :loading="isSubmitting"
          @click="handleSubmit"
        >
          立即登录
        </el-button>

        <div class="register-link">
          还没有账号？
          <router-link to="/register">立即注册</router-link>
        </div>
      </el-form>

      <div class="security-note">
        <el-icon><Lock /></el-icon>
        <span>已启用加密传输与安全日志</span>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElNotification } from 'element-plus'
import { Lock, Message } from '@element-plus/icons-vue'
import { http } from '../api/http'
import { useUserStore } from '../stores/useUserStore'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const formRef = ref(null)
const isSubmitting = ref(false)
const errorMessage = ref('')

const form = reactive({
  email: '',
  password: '',
  remember: true
})

const rules = {
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ]
}

onMounted(() => {
  const savedEmail = localStorage.getItem('kp:login-email')
  if (savedEmail) {
    form.email = savedEmail
  }
})

const handleSubmit = async () => {
  if (isSubmitting.value) return
  if (!formRef.value) return

  errorMessage.value = ''
  try {
    await formRef.value.validate()
  } catch (error) {
    return
  }

  isSubmitting.value = true
  try {
    const result = await http.postForm('/user/Login', {
      email: form.email,
      password: form.password
    })

    // 保存 token（Sa-Token 默认会将 token 存储在响应头 satoken 中）
    const token = result?.token || localStorage.getItem('kp:token')
    if (token) {
      localStorage.setItem('kp:token', token)
    }

    if (form.remember) {
      localStorage.setItem('kp:login-email', form.email)
    } else {
      localStorage.removeItem('kp:login-email')
    }

    await userStore.fetchProfile(true)

    ElNotification.success({
      title: '欢迎回来',
      message: '你的星球已经准备就绪。'
    })

    const redirectTo =
      typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.replace(redirectTo)
  } catch (error) {
    errorMessage.value = error?.message || '登录失败，请稍后再试'
  } finally {
    isSubmitting.value = false
  }
}

</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  background: 
    radial-gradient(circle at top right, rgba(var(--accent-rgb), 0.1), transparent 40%),
    radial-gradient(circle at bottom left, rgba(31, 156, 146, 0.1), transparent 40%);
}

.login-panel {
  width: min(480px, 100%);
  padding: 48px;
  border-radius: var(--radius-xl);
  background: rgba(255, 255, 255, 0.4);
  backdrop-filter: blur(30px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: var(--shadow-lg);
  display: grid;
  gap: 32px;
  animation: fadeUp 1.2s var(--spring) both;
}

.panel-header {
  text-align: center;
  display: grid;
  gap: 12px;
}

.panel-chip {
  justify-self: center;
  padding: 6px 14px;
  border-radius: 99px;
  background: rgba(var(--accent-rgb), 0.1);
  color: var(--accent);
  font-weight: 800;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.1em;
}

.panel-header h2 {
  font-size: 28px;
  letter-spacing: -0.04em;
}

.panel-subtitle {
  margin: 0;
  color: var(--muted);
  font-size: 15px;
  line-height: 1.5;
}

.login-form :deep(.el-form-item__label) {
  font-weight: 700;
  color: var(--ink);
  font-size: 14px;
  padding-bottom: 8px;
}

.login-form :deep(.el-input__wrapper) {
  border-radius: 14px;
  box-shadow: none !important;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.5);
  height: 48px;
  transition: var(--transition-all);
}

.login-form :deep(.el-input__wrapper.is-focus) {
  border-color: var(--accent);
  background: #fff;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(var(--accent-rgb), 0.1) !important;
}

.login-button {
  width: 100%;
  height: 52px;
  border-radius: 16px;
  font-weight: 700;
  font-size: 16px;
  letter-spacing: -0.01em;
  background: var(--accent);
  border: none;
  box-shadow: 0 8px 24px rgba(var(--accent-rgb), 0.25);
  margin-top: 12px;
  transition: var(--transition-all);
}

.login-button:hover {
  transform: translateY(-3px);
  box-shadow: 0 12px 32px rgba(var(--accent-rgb), 0.35);
  background: var(--accent);
}

.register-link {
  text-align: center;
  font-size: 14px;
  color: var(--muted);
}

.register-link a {
  color: var(--accent);
  font-weight: 700;
  margin-left: 4px;
}

.security-note {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 500;
  opacity: 0.7;
}

@keyframes fadeUp {
  from {
    opacity: 0;
    transform: translateY(30px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
</style>