import { defineStore } from 'pinia'
import { http } from '../api/http'

export const useFeedStore = defineStore('feed', {
  state: () => ({
    posts: [],
    isLoading: false,
    error: null
  }),
  actions: {
    async fetchPosts({ planetId, keyword }) {
      this.isLoading = true
      this.error = null
      try {
        if (!planetId) {
          this.posts = []
          return
        }

        const params = new URLSearchParams()
        if (planetId) params.set('planetId', String(planetId))
        if (keyword) params.set('keyword', keyword)

        const endpoint = keyword ? '/postings/search' : '/postings'
        const data = await http.get(`${endpoint}?${params.toString()}`)
        this.posts = Array.isArray(data) ? data : data?.records || []
      } catch (error) {
        this.error = error.message
        this.posts = []
      } finally {
        this.isLoading = false
      }
    },
    async fetchPush() {
      this.isLoading = true
      this.error = null
      try {
        const data = await http.get('/push/a')
        this.posts = Array.isArray(data) ? data : data?.data || []
      } catch (error) {
        this.error = error.message
        this.posts = []
      } finally {
        this.isLoading = false
      }
    }
  }
})



