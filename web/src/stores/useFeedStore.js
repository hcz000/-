import { defineStore } from 'pinia'
import { http } from '../api/http'

export const useFeedStore = defineStore('feed', {
  state: () => ({
    posts: [],
    isLoading: false,
    error: null,
    hasMore: false
  }),
  actions: {
    async fetchPosts({ planetId, keyword, cursor, lastId }) {
      this.isLoading = true
      this.error = null
      try {
        if (!planetId) {
          this.posts = []
          this.hasMore = false
          return
        }

        const params = new URLSearchParams()
        if (planetId) params.set('planetId', String(planetId))
        if (keyword) params.set('keyword', keyword)
        if (cursor) params.set('cursor', cursor)
        if (lastId) params.set('lastId', String(lastId))

        const endpoint = keyword ? '/postings/search' : '/postings'
        const data = await http.get(`${endpoint}?${params.toString()}`)

        if (keyword) {
          // 搜索仍是 offset 分页
          this.posts = Array.isArray(data) ? data : data?.records || []
        } else {
          // 游标分页
          const records = Array.isArray(data) ? data : data?.records || []
          if (cursor) {
            this.posts = [...this.posts, ...records]
          } else {
            this.posts = records
          }
          this.hasMore = data?.hasMore ?? false
        }
      } catch (error) {
        this.error = error.message
        if (!cursor) this.posts = []
        this.hasMore = false
      } finally {
        this.isLoading = false
      }
    },
    async fetchPush(mode = 'a') {
      this.isLoading = true
      this.error = null
      try {
        const endpointMap = {
          a: '/push/a',
          interest: '/push/interest',
          hot: '/push/hot',
          planet: '/push/planet',
          friends: '/push/friends',
          random: '/push/random'
        }
        const data = await http.get(endpointMap[mode] || endpointMap.a)
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



