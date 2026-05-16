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
import { computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from './stores/useUserStore'
import SidebarNav from './components/SidebarNav.vue'

const route = useRoute()
const userStore = useUserStore()
const isAuthLayout = computed(() => route.meta.layout === 'auth')

// 监听用户登录状态，启动/停止通知轮询
watch(
  () => userStore.isAuthenticated,
  (isAuth) => {
    if (isAuth) {
      // 登录后启动通知轮询（5分钟）
      userStore.startNotificationPolling(5 * 60 * 1000)
    } else {
      userStore.stopNotificationPolling()
    }
  },
  { immediate: true }
)

onMounted(() => {
  // 页面加载时，如果已登录，拉取一次未读数
  if (userStore.isAuthenticated) {
    userStore.fetchUnreadNotificationCount()
  }
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
