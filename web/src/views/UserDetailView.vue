<template>
  <section class="content">
    <div class="content-header">
      <div>
        <h2>用户详情</h2>
        <p class="upload-hint">查看用户信息并发送好友申请。</p>
      </div>
      <el-button size="large" round @click="router.back()">
        <el-icon><ArrowLeft /></el-icon>
        返回
      </el-button>
    </div>

    <el-alert
      v-if="errorMessage"
      type="error"
      :closable="false"
      show-icon
      :title="errorMessage"
      class="surface-card"
    />

    <div v-if="isLoading">
      <el-skeleton :rows="4" animated />
    </div>
    <div v-else-if="user" class="user-detail-card">
      <el-card class="surface-card" shadow="never">
        <div class="user-profile">
          <div class="user-avatar-section">
            <el-avatar :size="100" :src="user.avatar" />
            <div class="user-info">
              <h2>{{ user.username || '未设置用户名' }}</h2>
              <p class="upload-hint">{{ user.email }}</p>
              <div class="user-stats">
                <el-tag type="info" effect="light">
                  <el-icon><Calendar /></el-icon>
                  注册时间: {{ user.createTime || '未知' }}
                </el-tag>
              </div>
            </div>
          </div>

          <div class="user-actions">
            <el-button
              v-if="!isSelf && !isFriend"
              type="primary"
              size="large"
              round
              :loading="requestSending"
              @click="sendFriendRequest"
            >
              <el-icon><UserFilled /></el-icon>
              加好友
            </el-button>
            <el-button
              v-if="!isSelf && isFriend"
              type="success"
              size="large"
              round
              @click="openChat"
            >
              <el-icon><ChatDotRound /></el-icon>
              发消息
            </el-button>
            <el-tag v-if="isSelf" type="warning" effect="light" size="large">
              这是你的账号
            </el-tag>
            <el-tag v-if="!isSelf && isFriend" type="success" effect="light" size="large">
              <el-icon><Check /></el-icon>
              已是好友
            </el-tag>
          </div>
        </div>
      </el-card>

      <!-- 好友申请对话框 -->
      <el-dialog v-model="requestDialogVisible" width="500px" title="发送好友申请" class="request-dialog">
        <el-form label-position="top">
          <el-form-item label="附言（可选）">
            <el-input
              v-model="requestMessage"
              type="textarea"
              :rows="3"
              placeholder="简单介绍一下自己..."
              size="large"
            />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button round @click="requestDialogVisible = false">取消</el-button>
          <el-button type="primary" round :loading="requestSending" @click="confirmSendRequest">
            发送申请
          </el-button>
        </template>
      </el-dialog>
    </div>
  </section>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, UserFilled, ChatDotRound, Calendar, Check } from '@element-plus/icons-vue'
import { http } from '../api/http'
import { useUserStore } from '../stores/useUserStore'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const user = ref(null)
const isLoading = ref(false)
const errorMessage = ref('')
const isFriend = ref(false)
const requestDialogVisible = ref(false)
const requestMessage = ref('')
const requestSending = ref(false)

const isSelf = computed(() => user.value && user.value.id === userStore.id)

const fetchUser = async () => {
  const userId = route.params.id
  if (!userId) {
    errorMessage.value = '用户ID不存在'
    return
  }
  isLoading.value = true
  errorMessage.value = ''
  try {
    const data = await http.get(`/user/info/${userId}`)
    user.value = data
    // 检查是否是好友
    if (user.value && !isSelf.value) {
      const friends = await http.get('/friends')
      const friendList = Array.isArray(friends) ? friends : []
      isFriend.value = friendList.some(f => f.userId === user.value.id)
    }
  } catch (error) {
    errorMessage.value = error.message || '加载用户信息失败'
    user.value = null
  } finally {
    isLoading.value = false
  }
}

const sendFriendRequest = () => {
  requestDialogVisible.value = true
  requestMessage.value = ''
}

const confirmSendRequest = async () => {
  if (requestSending.value) return
  requestSending.value = true
  try {
    await http.post('/friends/request', {
      targetUserId: user.value.id,
      message: requestMessage.value.trim() || ''
    })
    ElMessage.success('好友申请已发送')
    requestDialogVisible.value = false
  } catch (error) {
    ElMessage.error(error.message || '发送申请失败')
  } finally {
    requestSending.value = false
  }
}

const openChat = () => {
  // 跳转到好友页面并打开聊天
  router.push('/friends')
}

onMounted(() => {
  fetchUser()
})
</script>

<style scoped>
.user-detail-card {
  max-width: 600px;
}

.user-profile {
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding: 24px;
}

.user-avatar-section {
  display: flex;
  align-items: flex-start;
  gap: 24px;
}

.user-info h2 {
  font-size: 24px;
  color: var(--ink);
  margin-bottom: 8px;
}

.user-stats {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}

.user-actions {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.user-actions .el-button--primary {
  background: var(--accent);
  border: none;
  box-shadow: 0 4px 12px rgba(var(--accent-rgb), 0.2);
  transition: var(--transition-all);
}

.user-actions .el-button--primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(var(--accent-rgb), 0.3);
  background: var(--accent);
}

.request-dialog :deep(.el-dialog) {
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: var(--shadow-lg);
}

.request-dialog :deep(.el-dialog__header) {
  padding: 20px 24px;
  border-bottom: 1px solid var(--border);
}

.request-dialog :deep(.el-dialog__title) {
  font-size: 20px;
  font-weight: 700;
  color: var(--ink);
}

.request-dialog :deep(.el-dialog__body) {
  padding: 24px;
}
</style>