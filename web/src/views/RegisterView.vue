<template>
  <div class="register-page">
    <section class="register-panel">
      <div class="panel-header">
        <span class="panel-chip">加入星球</span>
        <h2>创建你的账号</h2>
        <p class="panel-subtitle">开启你的知识探索之旅。</p>
      </div>

      <el-alert
        v-if="errorMessage"
        type="error"
        :closable="false"
        show-icon
        :title="errorMessage"
        class="panel-alert surface-card"
      />

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="register-form"
        @submit.prevent
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            :prefix-icon="Key"
            autocomplete="username"
            clearable
            size="large"
          />
        </el-form-item>

        <el-form-item label="邮箱" prop="email">
          <el-input
            v-model="form.email"
            placeholder="name@example.com"
            :prefix-icon="Message"
            autocomplete="email"
            clearable
            size="large"
            @input="validateEmailRealtime"
          />
        </el-form-item>

        <el-form-item label="验证码" prop="code">
          <div class="code-input-wrapper">
            <el-input
              v-model="form.code"
              placeholder="请输入验证码"
              :prefix-icon="Key"
              autocomplete="off"
              clearable
              size="large"
            />
            <el-button
              type="primary"
              class="send-code-btn"
              :loading="isSendingCode"
              :disabled="countdown > 0 || !form.emailValid"
              @click="handleSendCode"
            >
              {{ countdown > 0 ? `${countdown}s 后重发` : '发送验证码' }}
            </el-button>
          </div>
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            show-password
            autocomplete="new-password"
            size="large"
          />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            placeholder="请再次输入密码"
            :prefix-icon="Lock"
            show-password
            autocomplete="new-password"
            size="large"
          />
        </el-form-item>

        <el-button
          type="primary"
          class="register-button"
          :loading="isSubmitting"
          @click="handleSubmit"
        >
          立即注册
        </el-button>
      </el-form>

      <div class="form-footer">
        <span>已有账号？</span>
        <el-button type="primary" link class="login-link" @click="handleLogin">
          立即登录
        </el-button>
      </div>

      <div class="security-note">
        <el-icon><Lock /></el-icon>
        <span>已启用加密传输与安全日志</span>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElNotification } from 'element-plus'
import { Lock, Message, Key } from '@element-plus/icons-vue'
import { http } from '../api/http'

const router = useRouter()
const formRef = ref(null)
const isSubmitting = ref(false)
const isSendingCode = ref(false)
const errorMessage = ref('')
const countdown = ref(0)
let countdownTimer = null

const form = reactive({
  username: '',
  email: '',
  code: '',
  password: '',
  confirmPassword: '',
  emailValid: false
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '用户名长度在 2 到 20 个字符之间', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

onMounted(() => {
  const savedEmail = localStorage.getItem('kp:register-email')
  if (savedEmail) {
    form.email = savedEmail
    validateEmailRealtime()
  }
})

onUnmounted(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
})

// 实时验证邮箱格式
const validateEmailRealtime = () => {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  form.emailValid = emailRegex.test(form.email)
}

const handleSendCode = async () => {
  if (!form.emailValid) {
    errorMessage.value = '请输入有效的邮箱地址'
    return
  }

  errorMessage.value = ''
  isSendingCode.value = true

  try {
    await http.postForm('/user/send', {
      email: form.email
    })

    ElNotification.success({
      title: '验证码已发送',
      message: '验证码已发送到你的邮箱，请注意查收。'
    })

    localStorage.setItem('kp:register-email', form.email)
    startCountdown()
  } catch (error) {
    errorMessage.value = error?.message || '发送验证码失败，请稍后再试'
  } finally {
    isSendingCode.value = false
  }
}

const startCountdown = () => {
  countdown.value = 60
  countdownTimer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}

const handleSubmit = async () => {
  if (isSubmitting.value) return
  if (!formRef.value) return

  errorMessage.value = ''
  try {
    await formRef.value.validate()
  } catch (error) {
    return
  }

  if (form.password !== form.confirmPassword) {
    errorMessage.value = '两次输入的密码不一致'
    return
  }

  isSubmitting.value = true
  try {
    await http.postForm('/user/registered', {
      username: form.username,
      email: form.email,
      password: form.password,
      code: form.code
    })

    ElNotification.success({
      title: '注册成功',
      message: '欢迎加入知识星球！'
    })

    localStorage.removeItem('kp:register-email')
    router.replace('/login')
  } catch (error) {
    errorMessage.value = error?.message || '注册失败，请稍后再试'
  } finally {
    isSubmitting.value = false
  }
}

const handleLogin = () => {
  router.push('/login')
}
</script>

<style scoped>
.register-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  background: 
    radial-gradient(circle at top left, rgba(var(--accent-rgb), 0.1), transparent 40%),
    radial-gradient(circle at bottom right, rgba(31, 156, 146, 0.1), transparent 40%);
}

.register-panel {
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

.register-form :deep(.el-form-item__label) {
  font-weight: 700;
  color: var(--ink);
  font-size: 14px;
  padding-bottom: 8px;
}

.register-form :deep(.el-input__wrapper) {
  border-radius: 14px;
  box-shadow: none !important;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.5);
  height: 48px;
  transition: var(--transition-all);
}

.register-form :deep(.el-input__wrapper.is-focus) {
  border-color: var(--accent);
  background: #fff;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(var(--accent-rgb), 0.1) !important;
}

.code-input-wrapper {
  display: flex;
  gap: 12px;
}

.send-code-btn {
  height: 48px;
  border-radius: 14px;
  font-weight: 700;
}

.register-button {
  width: 100%;
  height: 52px;
  border-radius: 16px;
  font-weight: 700;
  font-size: 16px;
  background: var(--accent);
  border: none;
  box-shadow: 0 8px 24px rgba(var(--accent-rgb), 0.25);
  margin-top: 12px;
  transition: var(--transition-all);
}

.register-button:hover {
  transform: translateY(-3px);
  box-shadow: 0 12px 32px rgba(var(--accent-rgb), 0.35);
}

.form-footer {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--muted);
}

.login-link {
  font-weight: 700;
  color: var(--accent) !important;
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
