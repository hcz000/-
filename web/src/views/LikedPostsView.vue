<template>
  <section class="content">
    <div class="content-header">
      <div>
        <h2>喜欢</h2>
        <p class="upload-hint">你点赞过的帖子会在这里展示。</p>
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
    <div v-else-if="posts.length === 0" class="surface-card" style="padding: 24px;">
      <el-empty description="暂无喜欢的帖子" />
    </div>
    <div v-else class="feed-grid">
      <PostCard
        v-for="post in posts"
        :key="post.postingsId || post.id"
        :post="post"
        @deleted="handlePostDeleted"
      />
    </div>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import PostCard from '../components/PostCard.vue'
import { http } from '../api/http'

const posts = ref([])
const isLoading = ref(false)
const errorMessage = ref('')

const fetchLiked = async () => {
  isLoading.value = true
  errorMessage.value = ''
  try {
    const data = await http.get('/postings/liked?pageNum=1&pageSize=20')
    posts.value = Array.isArray(data) ? data : data?.records || []
  } catch (error) {
    errorMessage.value = error.message || '加载喜欢的帖子失败'
    posts.value = []
  } finally {
    isLoading.value = false
  }
}

const handlePostDeleted = (postId) => {
  posts.value = posts.value.filter(p => (p.postingsId || p.id) !== postId)
}

onMounted(() => {
  fetchLiked()
})
</script>
