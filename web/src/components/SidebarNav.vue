<template>
  <aside class="sidebar">
    <div class="brand">
      <div class="brand-row">
        <h1>Knowledge</h1>
        <span class="brand-badge">Beta</span>
      </div>
      <p class="upload-hint">学习与共创的实时星球</p>
    </div>

    <el-menu
      class="sidebar-menu"
      :default-active="route.path"
      router
    >
      <el-menu-item index="/">
        <el-icon><House /></el-icon>
        <span>首页</span>
      </el-menu-item>
      <el-menu-item index="/friends">
        <el-icon><User /></el-icon>
        <span>好友</span>
      </el-menu-item>
      <el-menu-item index="/community">
        <el-icon><ChatLineRound /></el-icon>
        <span>社区</span>
      </el-menu-item>
      <el-menu-item index="/notifications">
        <el-badge
          v-if="unreadCount > 0"
          :value="unreadCount"
          class="notification-badge"
        >
          <el-icon><Bell /></el-icon>
        </el-badge>
        <el-icon v-else><Bell /></el-icon>
        <span>通知</span>
      </el-menu-item>
      <el-menu-item index="/likes">
        <el-icon><Star /></el-icon>
        <span>喜欢</span>
      </el-menu-item>
      <el-menu-item index="/my-reports">
        <el-icon><WarningFilled /></el-icon>
        <span>我的举报</span>
      </el-menu-item>
    </el-menu>

    <div class="avatar-action">
      <el-upload
        action="/api/users/avatar"
        :show-file-list="false"
        :auto-upload="false"
        :on-change="handleAvatarChange"
      >
        <el-avatar
          :size="48"
          :src="avatarUrl || defaultAvatar"
        />
      </el-upload>
      <div class="user-info">
        <div class="user-name">{{ displayName }}</div>
        <div class="user-meta">
          <small>{{ displayRole }}</small>
          <button
            v-if="isAuthed"
            class="logout-btn"
            type="button"
            title="退出登录"
            @click="handleLogout"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path>
              <polyline points="16 17 21 12 16 7"></polyline>
              <line x1="21" y1="12" x2="9" y2="12"></line>
            </svg>
          </button>
        </div>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import { Bell, ChatLineRound, House, Star, User, WarningFilled } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import { useUserStore } from '../stores/useUserStore'
import { http } from '../api/http'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { avatarUrl, name, role, unreadNotificationCount } = storeToRefs(userStore)

const defaultAvatar =
  'https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?auto=format&fit=crop&w=200&q=80'

const displayName = computed(() => name.value || '未登录')
const displayRole = computed(() => role.value || userStore.status)
const isAuthed = computed(() => userStore.isAuthenticated)
const unreadCount = computed(() => unreadNotificationCount.value)

onMounted(() => {
  userStore.fetchProfile()
})

const handleLogout = async () => {
  await userStore.logout()
  ElMessage.success('已退出登录')
  router.replace('/login')
}

const handleAvatarChange = async (uploadFile) => {
  const file = uploadFile.raw
  if (!file) return

  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请上传图片格式的头像')
    return
  }

  if (file.size > 50 * 1024 * 1024) {
    ElMessage.warning('图片大小不要超过 50MB')
    return
  }

  const previewUrl = URL.createObjectURL(file)
  userStore.setAvatar(previewUrl)

  const uploadedUrl = await userStore.uploadAvatar(file)
  if (uploadedUrl) {
    ElNotification.success({
      title: '头像更新成功',
      message: '你的新头像已同步到后台。'
    })
  } else {
    ElMessage.info('头像已在本地更新，后端接口暂未返回')
  }
}
</script>

<style scoped>
.notification-badge :deep(.el-badge__content) {
  background: var(--accent);
}
</style>

