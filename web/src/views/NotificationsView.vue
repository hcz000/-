<template>
  <section class="content">
    <div class="content-header">
      <div>
        <h2>通知中心</h2>
        <p class="upload-hint">所有系统与社区提醒都在这里。</p>
      </div>
      <el-button type="primary" size="large" round :loading="isMarking" @click="markAllRead">
        全部已读
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
    <div v-else-if="notifications.length === 0" class="surface-card" style="padding: 24px;">
      <el-empty description="暂无通知" />
    </div>
    <el-card v-else class="surface-card" shadow="never" style="padding: 24px;">
      <el-timeline class="notification-timeline">
        <el-timeline-item
          v-for="note in notifications"
          :key="note.id"
          :timestamp="note.createTime"
          placement="top"
        >
          <div class="timeline-item-content" :class="{ unread: !note.readFlag, read: note.readFlag }">
            <h3 style="margin: 0;">{{ note.title }}</h3>
            <el-tag v-if="!note.readFlag" type="warning" size="small" round>未读</el-tag>
            <el-tag v-else type="info" size="small" round>已读</el-tag>
          </div>
          <p class="upload-hint" style="margin-top: 6px;">{{ note.content }}</p>
          <div class="item-actions">
            <el-button
              v-if="!note.readFlag"
              size="small"
              text
              type="primary"
              :loading="note._reading === true"
              @click="markOneRead(note)"
            >
              已读
            </el-button>
          </div>
          <el-button
            v-if="note.postId"
            size="small"
            type="primary"
            round
            style="margin-top: 10px;"
            @click="goPost(note.postId)"
          >
            查看帖子
            <el-icon class="el-icon--right"><ArrowRight /></el-icon>
          </el-button>
        </el-timeline-item>
      </el-timeline>
    </el-card>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight } from '@element-plus/icons-vue'
import { http } from '../api/http'

const router = useRouter()
const notifications = ref([])
const isLoading = ref(false)
const isMarking = ref(false)
const errorMessage = ref('')

const normalizeList = (data) => {
  if (Array.isArray(data)) return data
  if (Array.isArray(data?.records)) return data.records
  if (Array.isArray(data?.content)) return data.content
  return []
}

const fetchNotifications = async () => {
  isLoading.value = true
  errorMessage.value = ''
  try {
    const data = await http.get('/t-notification?page=1&size=20')
    notifications.value = normalizeList(data)
  } catch (error) {
    errorMessage.value = error.message || '加载通知失败'
    notifications.value = []
  } finally {
    isLoading.value = false
  }
}

const markAllRead = async () => {
  if (isMarking.value) return
  const unread = notifications.value.filter((note) => !note.readFlag)
  if (unread.length === 0) return

  isMarking.value = true
  try {
    await Promise.all(unread.map((note) => http.post(`/t-notification/${note.id}/read`, {})))
    await fetchNotifications()
  } catch (error) {
    errorMessage.value = error.message || '标记已读失败'
  } finally {
    isMarking.value = false
  }
}

const markOneRead = async (note) => {
  if (!note || note.readFlag || note._reading) return
  note._reading = true
  try {
    await http.post(`/t-notification/${note.id}/read`, {})
    note.readFlag = true
  } catch (error) {
    errorMessage.value = error.message || '标记已读失败'
  } finally {
    note._reading = false
  }
}

const goPost = (postId) => {
  router.push(`/posts/${postId}`)
}

onMounted(() => {
  fetchNotifications()
})
</script>

<style scoped>
.notification-timeline :deep(.el-timeline-item__timestamp) {
  color: var(--muted);
  font-size: 13px;
}

.notification-timeline :deep(.el-timeline-item__content) {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.notification-timeline :deep(.el-timeline-item__content h3) {
  font-size: 16px;
  color: var(--ink);
}

.timeline-item-content {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.timeline-item-content.unread h3 {
  font-weight: 700;
}

.timeline-item-content.read h3 {
  font-weight: 500;
  opacity: 0.82;
}

.item-actions {
  margin-top: 6px;
}

.el-button--primary {
  background: var(--accent);
  border: none;
  box-shadow: 0 4px 12px rgba(var(--accent-rgb), 0.2);
  transition: var(--transition-all);
}

.el-button--primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(var(--accent-rgb), 0.3);
  background: var(--accent);
}
</style>
