import { defineStore } from 'pinia'
import { http } from '../api/http'

export const useUserStore = defineStore('user', {
  state: () => ({
    id: null,
    name: '',
    role: '',
    avatarUrl: '',
    status: '',
    profileLoaded: false,
    unreadNotificationCount: 0,
    communityPollingTimer: null
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.id),
    hasUnreadNotifications: (state) => state.unreadNotificationCount > 0
  },
  actions: {
    setAvatar(url) {
      this.avatarUrl = url
    },
    clearSession() {
      this.id = null
      this.name = ''
      this.role = ''
      this.avatarUrl = ''
      this.status = ''
      this.unreadNotificationCount = 0
      this.stopCommunityPolling()
      this.profileLoaded = true
    },
    async fetchProfile(force = false) {
      if (force) {
        this.profileLoaded = false
      }
      if (this.profileLoaded) return
      try {
        const profile = await http.get('/user/profile')
        if (profile) {
          this.id = profile.id ?? null
          this.name = profile.username || profile.email || ''
          this.role = profile.email || ''
          this.avatarUrl = profile.avatar || ''
          this.status = '在线'
        }
      } catch (error) {
        this.clearSession()
      } finally {
        this.profileLoaded = true
      }
    },
    async logout() {
      try {
        await http.get('/user/logout')
      } catch (error) {
        // ignore backend errors on logout to ensure local state clears
      } finally {
        // 清除本地 token
        localStorage.removeItem('kp:token')
        sessionStorage.removeItem('kp:token')
        this.clearSession()
      }
    },
    async uploadAvatar(file) {
      const formData = new FormData()
      formData.append('file', file)

      try {
        const response = await http.upload('/user/avatar', formData)
        const nextUrl =
          typeof response === 'string' ? response : response?.url || ''
        if (nextUrl) {
          this.avatarUrl = nextUrl
        }
        return nextUrl
      } catch (error) {
        return ''
      }
    },
    // 获取未读通知数量（上线时拉取一次）
    async fetchUnreadNotificationCount() {
      if (!this.isAuthenticated) return
      try {
        const count = await http.get('/t-notification/unread-count')
        this.unreadNotificationCount = typeof count === 'number' ? count : 0
      } catch (error) {
        // ignore errors
      }
    },
    // 启动社区页面轮询（2分钟）
    startCommunityPolling(fetchCallback, intervalMs = 2 * 60 * 1000) {
      this.stopCommunityPolling()
      if (fetchCallback) {
        fetchCallback()
        this.communityPollingTimer = setInterval(fetchCallback, intervalMs)
      }
    },
    // 停止社区轮询
    stopCommunityPolling() {
      if (this.communityPollingTimer) {
        clearInterval(this.communityPollingTimer)
        this.communityPollingTimer = null
      }
    }
  }
})
