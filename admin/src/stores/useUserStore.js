import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('adminToken') || '')
  const userInfo = ref(null)

  const setToken = (newToken) => {
    token.value = newToken
    if (newToken) {
      localStorage.setItem('adminToken', newToken)
    } else {
      localStorage.removeItem('adminToken')
    }
  }

  const setUserInfo = (info) => {
    userInfo.value = info
  }

  const logout = () => {
    setToken('')
    setUserInfo(null)
  }

  return {
    token,
    userInfo,
    setToken,
    setUserInfo,
    logout
  }
})
