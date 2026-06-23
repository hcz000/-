<template>
  <div v-if="isAuthLayout">
    <router-view v-slot="{ Component }">
      <transition name="fade" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
  </div>
  <div v-else class="app-shell">
    <SidebarNav />
    <router-view v-slot="{ Component }">
      <transition name="fade" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
  </div>
</template>

<script setup>
import { computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from './stores/useUserStore'
import { connect, disconnect, on } from './utils/wsClient'
import SidebarNav from './components/SidebarNav.vue'

const route = useRoute()
const userStore = useUserStore()
const isAuthLayout = computed(() => route.meta.layout === 'auth')

// 监听用户登录状态，建立/断开全局 WebSocket
let unsubNotification = null
watch(
  () => userStore.isAuthenticated,
  (isAuth) => {
    if (isAuth) {
      connect()
      // 拉取一次离线期间积压的未读数
      userStore.fetchUnreadNotificationCount()
      // 实时推送：收到 NOTIFICATION 时直接 +1
      unsubNotification = on('NOTIFICATION', () => {
        userStore.unreadNotificationCount++
      })
    } else {
      unsubNotification?.()
      unsubNotification = null
      disconnect()
    }
  },
  { immediate: true }
)

onMounted(() => {
  if (userStore.isAuthenticated) {
    userStore.fetchUnreadNotificationCount()
  }
})

onUnmounted(() => {
  unsubNotification?.()
  disconnect()
})
</script>

<style>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.fade-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
