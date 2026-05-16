<template>
  <section class="content">
    <!-- 好友列表视图 -->
    <template v-if="!chatMode">
      <div class="content-header">
        <div>
          <h2>好友</h2>
          <p class="upload-hint">保持和伙伴的节奏一致。</p>
        </div>
        <div style="display: flex; gap: 12px;">
          <el-button type="primary" size="large" round @click="openInviteDialog">
            邀请好友
          </el-button>
          <el-button size="large" round @click="openRequestsDialog" :disabled="pendingCount === 0">
            <el-icon><Bell /></el-icon>
            好友申请
            <el-badge v-if="pendingCount > 0" :value="pendingCount" type="warning" />
          </el-button>
        </div>
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
      <div v-else-if="friends.length === 0" class="surface-card" style="padding: 24px;">
        <el-empty description="暂无好友" />
      </div>
      <div v-else class="feed-grid">
        <el-card v-for="friend in friends" :key="friend.userId" class="surface-card" shadow="never">
          <div class="friend-card-content">
            <div class="friend-info">
              <el-avatar :size="56" :src="friend.avatar" />
              <div>
                <h3>{{ friend.username || friend.email || `用户 ${friend.userId}` }}</h3>
                <p class="upload-hint" style="margin-top: 4px;">{{ friend.email }}</p>
              </div>
            </div>
            <div class="friend-actions">
              <el-button type="primary" round @click="openChat(friend)">
                发消息
                <el-icon class="el-icon--right"><Message /></el-icon>
              </el-button>
            </div>
          </div>
        </el-card>
      </div>
    </template>

    <!-- 聊天视图 -->
    <template v-else>
      <div class="chat-view">
        <div class="chat-header">
          <el-button round @click="closeChat">
            <el-icon><ArrowLeft /></el-icon>
            返回
          </el-button>
          <div class="chat-header-info">
            <el-avatar :size="40" :src="activeFriend?.avatar" />
            <div>
              <h2>{{ activeFriend?.username || activeFriend?.email || `用户 ${activeFriend?.userId}` }}</h2>
              <div class="chat-status" :class="{ online: wsConnected }">
                <span :class="{ 'status-dot': true, 'is-online': wsConnected }"></span>
                {{ wsConnected ? '在线' : '离线' }}
              </div>
            </div>
          </div>
        </div>

        <div class="chat-container">
          <div v-if="chatLoading" class="chat-loading">
            <el-skeleton :rows="6" animated />
          </div>
          <div v-else ref="chatListRef" class="chat-list">
            <div
              v-for="message in chatMessages"
              :key="message.id"
              class="chat-item"
              :class="{ me: String(message.senderId) === String(userStore.id) }"
            >
              <div class="chat-bubble">
                <div class="chat-content">{{ message.content }}</div>
                <div class="chat-time">{{ message.createTime }}</div>
              </div>
            </div>
            <el-empty v-if="chatMessages.length === 0" description="暂无消息，发送第一条消息吧" />
          </div>
        </div>

        <div class="chat-compose">
          <el-input
            v-model="chatDraft"
            type="textarea"
            :rows="3"
            placeholder="输入消息..."
            size="large"
            @keydown.enter.ctrl="sendChat"
          />
          <div class="chat-actions">
            <el-button type="primary" round :loading="chatSending" @click="sendChat">
              发送
              <el-icon class="el-icon--right"><Promotion /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
    </template>

    <!-- 邀请好友对话框 -->
    <el-dialog v-model="inviteVisible" width="500px" title="邀请好友" class="invite-dialog">
      <div class="invite-form">
        <el-form label-position="top">
          <el-form-item label="目标用户">
            <el-input
              v-model="inviteTarget"
              placeholder="输入用户邮箱或用户ID"
              size="large"
              clearable
            />
          </el-form-item>
          <el-form-item label="附言（可选）">
            <el-input
              v-model="inviteMessage"
              type="textarea"
              :rows="3"
              placeholder="简单介绍一下自己..."
              size="large"
            />
          </el-form-item>
        </el-form>
        <div v-if="searchedUser" class="searched-user-preview">
          <el-avatar :size="40" :src="searchedUser.avatar" />
          <div>
            <h4>{{ searchedUser.username || '未设置用户名' }}</h4>
            <p class="upload-hint">{{ searchedUser.email }}</p>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button round @click="inviteVisible = false">取消</el-button>
        <el-button type="primary" round :loading="inviteSending" @click="sendInvite">
          发送申请
        </el-button>
      </template>
    </el-dialog>

    <!-- 好友申请处理对话框 -->
    <el-dialog v-model="requestsVisible" width="600px" title="好友申请" class="requests-dialog">
      <div v-if="requestsLoading">
        <el-skeleton :rows="3" animated />
      </div>
      <div v-else-if="pendingRequests.length === 0" class="surface-card" style="padding: 24px;">
        <el-empty description="暂无待处理的好友申请" />
      </div>
      <div v-else class="requests-list">
        <el-card v-for="request in pendingRequests" :key="request.id" class="surface-card request-card" shadow="never">
          <div class="request-content">
            <div class="request-info">
              <el-avatar :size="48" :src="request.requesterAvatar" />
              <div>
                <h3>{{ request.requesterName || `用户 ${request.requesterId}` }}</h3>
                <p class="upload-hint" style="margin-top: 4px;">{{ request.requesterEmail || '' }}</p>
                <p v-if="request.message" class="request-message">{{ request.message }}</p>
              </div>
            </div>
            <div class="request-meta">
              <span class="time-text">{{ request.createTime }}</span>
            </div>
          </div>
          <div class="request-actions">
            <el-button
              type="success"
              round
              :loading="request.processing"
              :disabled="request.processing || !request.id"
              @click="respondRequest(request, true)"
            >
              同意
            </el-button>
            <el-button
              type="danger"
              round
              :loading="request.processing"
              :disabled="request.processing || !request.id"
              @click="respondRequest(request, false)"
            >
              拒绝
            </el-button>
          </div>
        </el-card>
      </div>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { http } from '../api/http'
import { Message, Promotion, Bell, ArrowLeft } from '@element-plus/icons-vue'
import { useUserStore } from '../stores/useUserStore'
import { ElMessage } from 'element-plus'

const friends = ref([])
const isLoading = ref(false)
const errorMessage = ref('')
const chatMode = ref(false)  // 是否进入聊天模式
const activeFriend = ref(null)
const chatMessages = ref([])
const chatLoading = ref(false)
const chatDraft = ref('')
const chatSending = ref(false)
const chatListRef = ref(null)
const userStore = useUserStore()
let wsClient = null
const wsConnected = ref(false)

// 邀请好友相关变量
const inviteVisible = ref(false)
const inviteTarget = ref('')
const inviteMessage = ref('')
const inviteSending = ref(false)
const searchedUser = ref(null)

// 好友申请处理相关变量
const requestsVisible = ref(false)
const requestsLoading = ref(false)
const pendingRequests = ref([])
const pendingCount = ref(0)
const requestActionLock = ref(new Set())

const chatTitle = computed(() => {
  if (!activeFriend.value) return '好友对话'
  const name =
    activeFriend.value.username ||
    activeFriend.value.email ||
    `用户 ${activeFriend.value.userId}`
  return `与 ${name} 对话`
})

const fetchFriends = async () => {
  isLoading.value = true
  errorMessage.value = ''
  try {
    const data = await http.get('/friends')
    friends.value = Array.isArray(data) ? data : []
  } catch (error) {
    errorMessage.value = error.message || '加载好友失败'
    friends.value = []
  } finally {
    isLoading.value = false
  }
}

const openChat = async (friend) => {
  activeFriend.value = friend
  chatMode.value = true  // 进入聊天模式，占满好友列表区域
  errorMessage.value = ''
  await fetchChatMessages()
}

const closeChat = () => {
  chatMode.value = false
  chatMessages.value = []
  chatDraft.value = ''
  errorMessage.value = ''
}

const fetchChatMessages = async () => {
  if (!activeFriend.value) return
  chatLoading.value = true
  try {
    const data = await http.get(
      `/chat/message?friendId=${activeFriend.value.userId}&pageNum=1&pageSize=50`
    )
    chatMessages.value = Array.isArray(data) ? data : data?.records || []
    await nextTick()
    scrollToBottom()
  } catch (error) {
    errorMessage.value = error.message || '加载聊天记录失败'
    chatMessages.value = []
  } finally {
    chatLoading.value = false
  }
}

const sendChat = async () => {
  if (!activeFriend.value || chatSending.value) return
  const content = chatDraft.value.trim()
  if (!content) {
    errorMessage.value = '消息内容不能为空'
    return
  }
  if (!wsClient || wsClient.readyState !== WebSocket.OPEN) {
    errorMessage.value = '实时连接未就绪，请稍后重试'
    connectWs()
    return
  }
  chatSending.value = true
  try {
    wsClient.send(JSON.stringify({
      friendId: activeFriend.value.userId,
      content
    }))
    chatDraft.value = ''
    // 由后端 WebSocket 广播回本端后统一入列表，避免重复插入
  } catch (error) {
    console.error('发送消息失败:', error)
    errorMessage.value = error.message || '发送失败'
  } finally {
    chatSending.value = false
  }
}

const getWsToken = () => {
  // 从 localStorage/sessionStorage 获取 token（与 HTTP 请求一致）
  return localStorage.getItem('kp:token') || sessionStorage.getItem('kp:token') || ''
}

const resolveWsUrl = () => {
  const envUrl = import.meta.env.VITE_WS_URL || ''
  let baseUrl = ''
  if (envUrl) {
    if (envUrl.startsWith('ws')) {
      baseUrl = envUrl
    } else if (envUrl.startsWith('http')) {
      const url = new URL(envUrl)
      const scheme = url.protocol === 'https:' ? 'wss:' : 'ws:'
      const basePath = url.pathname && url.pathname !== '/' ? url.pathname : ''
      baseUrl = `${scheme}//${url.host}${basePath}/ws/chat`
    } else if (envUrl.startsWith('/')) {
      const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
      baseUrl = `${scheme}://${window.location.host}${envUrl}`
    }
  } else {
    const apiBase = import.meta.env.VITE_API_BASE || ''
    if (apiBase && apiBase.startsWith('http')) {
      const url = new URL(apiBase)
      const scheme = url.protocol === 'https:' ? 'wss:' : 'ws:'
      baseUrl = `${scheme}//${url.host}/ws/chat`
    } else {
      const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
      baseUrl = `${scheme}://${window.location.host}/ws/chat`
    }
  }
  const token = getWsToken()
  if (token) {
    const separator = baseUrl.includes('?') ? '&' : '?'
    baseUrl = `${baseUrl}${separator}token=${encodeURIComponent(token)}`
  }
  return baseUrl
}

const connectWs = () => {
  if (wsClient) return  // 已连接则不再重复连接
  try {
    const url = resolveWsUrl()
    wsClient = new WebSocket(url)
    wsClient.onopen = () => {
      wsConnected.value = true
    }
    wsClient.onclose = () => {
      wsConnected.value = false
      wsClient = null
    }
    wsClient.onerror = () => {
      wsConnected.value = false
    }
    wsClient.onmessage = async (event) => {
      try {
        const payload = JSON.parse(event.data)
        if (payload?.type !== 'CHAT') return
        const message = payload.data
        if (!message) return

        // 判断消息是否与当前活跃好友相关（统一类型比较）
        const myId = String(userStore.id)
        const peerId = String(message.senderId) === myId ? String(message.receiverId) : String(message.senderId)

        if (activeFriend.value && String(activeFriend.value.userId) === peerId && chatMode.value) {
          // 正在和该好友聊天，直接显示消息
          const exists = chatMessages.value.some((item) => String(item?.id) === String(message.id))
          if (!exists) {
            chatMessages.value = [...chatMessages.value, message]
            await nextTick()
            scrollToBottom()
          }
        } else if (String(message.senderId) !== myId) {
          // 收到其他好友的消息，显示提示
          ElMessage.info(`收到来自好友的新消息`)
        }
      } catch (error) {
        // ignore malformed payloads
      }
    }
  } catch (error) {
    wsClient = null
  }
}

const disconnectWs = () => {
  if (wsClient) {
    try {
      wsClient.close()
    } catch (error) {
      // ignore close errors
    }
    wsClient = null
  }
  wsConnected.value = false
}

const scrollToBottom = () => {
  if (!chatListRef.value) return
  chatListRef.value.scrollTop = chatListRef.value.scrollHeight
}

// 邀请好友方法
const openInviteDialog = () => {
  inviteVisible.value = true
  inviteTarget.value = ''
  inviteMessage.value = ''
  searchedUser.value = null
}

const sendInvite = async () => {
  if (inviteSending.value) return
  const target = inviteTarget.value.trim()
  if (!target) {
    ElMessage.warning('请输入目标用户的邮箱或用户ID')
    return
  }
  inviteSending.value = true
  try {
    // 解析目标用户ID（支持邮箱或数字ID）
    let targetUserId = null
    if (/^\d+$/.test(target)) {
      targetUserId = Number(target)
    } else {
      // 通过邮箱查找用户
      const user = await http.get(`/user/search?email=${encodeURIComponent(target)}`)
      if (user && user.id) {
        targetUserId = user.id
        searchedUser.value = user
      } else {
        ElMessage.error('未找到该用户')
        return
      }
    }
    if (targetUserId === userStore.id) {
      ElMessage.warning('不能添加自己为好友')
      return
    }
    await http.post('/friends/request', {
      targetUserId,
      message: inviteMessage.value.trim() || ''
    })
    ElMessage.success('好友申请已发送')
    inviteVisible.value = false
  } catch (error) {
    ElMessage.error(error.message || '发送申请失败')
  } finally {
    inviteSending.value = false
  }
}

// 好友申请处理方法
const openRequestsDialog = async () => {
  requestsVisible.value = true
  await fetchPendingRequests()
}

const fetchPendingRequests = async () => {
  requestsLoading.value = true
  try {
    const data = await http.get('/friends/requests/pending')
    const requests = Array.isArray(data) ? data : []
    // 为每个请求添加申请者信息
    const enrichedRequests = await Promise.all(requests.map(async (request) => {
      try {
        const user = await http.get(`/user/info/${request.requesterId}`)
        return {
          ...request,
          id: request?.id ?? null,
          processing: false,
          requesterName: user?.username || '',
          requesterEmail: user?.email || '',
          requesterAvatar: user?.avatar || ''
        }
      } catch (error) {
        return {
          ...request,
          id: request?.id ?? null,
          processing: false,
          requesterName: '',
          requesterEmail: '',
          requesterAvatar: ''
        }
      }
    }))
    pendingRequests.value = enrichedRequests
  } catch (error) {
    ElMessage.error(error.message || '加载好友申请失败')
    pendingRequests.value = []
  } finally {
    requestsLoading.value = false
  }
}

const respondRequest = async (request, accept) => {
  if (!request?.id) {
    ElMessage.error('好友申请ID缺失，无法处理')
    return
  }
  if (requestActionLock.value.has(request.id)) {
    return
  }
  requestActionLock.value.add(request.id)
  request.processing = true
  try {
    await http.post(`/friends/request/${request.id}/respond?accept=${accept}`, {})
    ElMessage.success(accept ? '已同意好友申请' : '已拒绝好友申请')
    // 从列表中移除已处理的申请
    pendingRequests.value = pendingRequests.value.filter(r => r.id !== request.id)
    pendingCount.value = pendingRequests.value.length
    // 如果同意了，刷新好友列表
    if (accept) {
      await fetchFriends()
    }
    // 如果没有更多申请，关闭对话框
    if (pendingRequests.value.length === 0) {
      requestsVisible.value = false
    }
  } catch (error) {
    ElMessage.error(error.message || '处理失败')
  } finally {
    request.processing = false
    requestActionLock.value.delete(request.id)
  }
}

// 获取待处理好友申请数量
const fetchPendingCount = async () => {
  try {
    const data = await http.get('/friends/requests/pending')
    const requests = Array.isArray(data) ? data : []
    pendingCount.value = requests.length
  } catch (error) {
    // ignore
  }
}

onMounted(() => {
  fetchFriends()
  fetchPendingCount()
  connectWs()  // 页面加载时就建立WebSocket连接
})

onUnmounted(() => {
  disconnectWs()  // 页面卸载时断开WebSocket连接
})

// 监听 activeFriend 变化，当切换好友时重新拉取消息
watch(activeFriend, (friend) => {
  if (friend && chatMode.value) {
    fetchChatMessages()
  }
})

// 聊天模式下保持 WebSocket 连接
watch(chatMode, (value) => {
  if (value && !wsClient) {
    connectWs()
  }
})
</script>

<style scoped>
.friend-card-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 12px;
}

.friend-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.friend-info h3 {
  font-size: 18px;
  color: var(--ink);
}

.friend-actions {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
}

.friend-actions .el-button {
  font-weight: 600;
  height: 36px;
  padding: 0 16px;
  background: var(--accent);
  border: none;
  box-shadow: 0 4px 12px rgba(var(--accent-rgb), 0.2);
  transition: var(--transition-all);
}

.friend-actions .el-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(var(--accent-rgb), 0.3);
  background: var(--accent);
}

/* 聊天视图样式 - 与 content-header 卡片样式一致 */
.chat-view {
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.4);
  backdrop-filter: blur(20px);
  border-radius: var(--radius-xl);
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: var(--shadow-lg);
}

.chat-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 32px;
  background: transparent;
  border-bottom: 1px solid var(--border);
}

.chat-header-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.chat-header-info h2 {
  font-size: 20px;
  font-weight: 700;
  color: var(--ink);
  margin: 0;
}

.chat-header .chat-status {
  margin-top: 4px;
}

.chat-container {
  display: flex;
  flex-direction: column;
  padding: 24px 32px;
  min-height: 300px;
  max-height: 400px;
  overflow: hidden;
}

.chat-loading {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chat-status {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--muted);
  font-weight: 500;
}

.status-dot {
  width: 8px;
  height: 8px;
  background: var(--muted);
  border-radius: 50%;
  transition: var(--transition-all);
}

.status-dot.is-online {
  background: #1f7a3f;
}

.chat-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-right: 8px;
}

.chat-item {
  display: flex;
  justify-content: flex-start;
}

.chat-item.me {
  justify-content: flex-end;
}

.chat-bubble {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: var(--radius-md);
  background: rgba(var(--ink-rgb), 0.06);
  border: 1px solid var(--border);
  color: var(--ink);
  font-size: 15px;
  line-height: 1.6;
}

.chat-item.me .chat-bubble {
  background: rgba(var(--accent-rgb), 0.12);
  border-color: rgba(var(--accent-rgb), 0.2);
  color: var(--ink);
}

.chat-content {
  white-space: pre-wrap;
}

.chat-time {
  margin-top: 8px;
  font-size: 11px;
  color: var(--muted);
  font-weight: 500;
  text-align: right;
}

.chat-compose {
  padding: 24px 32px;
  border-top: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chat-compose :deep(.el-textarea__inner) {
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.5);
  box-shadow: none;
  border: 1px solid var(--border);
  transition: var(--transition-all);
}

.chat-compose :deep(.el-textarea__inner:focus) {
  border-color: var(--accent);
  background: #fff;
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.chat-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.chat-actions .el-button {
  border-radius: var(--radius-md);
  font-weight: 600;
  height: 38px;
  padding: 0 16px;
}

.chat-actions .el-button--primary {
  background: var(--accent);
  border: none;
  box-shadow: 0 4px 12px rgba(var(--accent-rgb), 0.2);
}

.chat-actions .el-button--primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(var(--accent-rgb), 0.3);
  background: var(--accent);
}

.chat-actions .el-button:not(.el-button--primary):hover {
  transform: translateY(-2px);
  border-color: var(--accent);
  color: var(--accent);
  background: rgba(var(--accent-rgb), 0.05);
}

/* 邀请好友对话框样式 */
.invite-dialog :deep(.el-dialog) {
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: var(--shadow-lg);
}

.invite-dialog :deep(.el-dialog__header) {
  padding: 20px 24px;
  border-bottom: 1px solid var(--border);
}

.invite-dialog :deep(.el-dialog__title) {
  font-size: 20px;
  font-weight: 700;
  color: var(--ink);
}

.invite-dialog :deep(.el-dialog__body) {
  padding: 24px;
}

.invite-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.searched-user-preview {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: rgba(var(--accent-rgb), 0.08);
  border-radius: var(--radius-md);
  border: 1px solid rgba(var(--accent-rgb), 0.2);
}

.searched-user-preview h4 {
  font-size: 16px;
  color: var(--ink);
  margin: 0;
}

/* 好友申请处理对话框样式 */
.requests-dialog :deep(.el-dialog) {
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: var(--shadow-lg);
}

.requests-dialog :deep(.el-dialog__header) {
  padding: 20px 24px;
  border-bottom: 1px solid var(--border);
}

.requests-dialog :deep(.el-dialog__title) {
  font-size: 20px;
  font-weight: 700;
  color: var(--ink);
}

.requests-dialog :deep(.el-dialog__body) {
  padding: 24px;
}

.requests-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.request-card {
  border-radius: var(--radius-md);
}

.request-content {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.request-info {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.request-info h3 {
  font-size: 16px;
  color: var(--ink);
  margin: 0;
}

.request-message {
  margin-top: 8px;
  font-size: 13px;
  color: var(--muted);
  line-height: 1.4;
}

.request-meta {
  font-size: 12px;
  color: var(--muted);
}

.time-text {
  color: var(--muted);
}

.request-actions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.request-actions .el-button {
  font-weight: 600;
  height: 36px;
  padding: 0 16px;
}
</style>
