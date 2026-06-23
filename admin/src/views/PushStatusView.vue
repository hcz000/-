<template>
  <section class="push-status">
    <div class="page-header">
      <h2>推送状态</h2>
      <p>查看热榜内容并手动刷新推荐候选。</p>
    </div>

    <el-card shadow="never" class="toolbar">
      <el-input-number v-model="limit" :min="20" :max="2000" :step="50" />
      <el-button type="primary" :loading="rebuilding" @click="rebuildHot">重建热榜</el-button>
      <el-button @click="fetchHotPosts">刷新列表</el-button>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="posts" border stripe>
        <el-table-column type="index" label="#" width="70" />
        <el-table-column prop="postingsId" label="帖子ID" width="130" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="type" label="类型" width="120" />
        <el-table-column prop="likeCount" label="点赞" width="90" />
        <el-table-column prop="replyCount" label="评论" width="90" />
        <el-table-column prop="createTime" label="发布时间" width="180" />
      </el-table>
    </el-card>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { http } from '../api/http'

const loading = ref(false)
const rebuilding = ref(false)
const limit = ref(500)
const posts = ref([])

const fetchHotPosts = async () => {
  loading.value = true
  try {
    posts.value = await http.get('/admin/hot/posts', { params: { size: 50 } }) || []
  } catch (error) {
    ElMessage.error(error.message || '获取热榜失败')
  } finally {
    loading.value = false
  }
}

const rebuildHot = async () => {
  rebuilding.value = true
  try {
    await http.post('/admin/hot/rebuild', null, { params: { limit: limit.value } })
    ElMessage.success('热榜已重建')
    fetchHotPosts()
  } catch (error) {
    ElMessage.error(error.message || '重建失败')
  } finally {
    rebuilding.value = false
  }
}

onMounted(fetchHotPosts)
</script>

<style scoped>
.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0 0 8px;
  font-size: 20px;
}

.page-header p {
  margin: 0;
  color: #909399;
}

.toolbar {
  margin-bottom: 20px;
}

.toolbar :deep(.el-card__body) {
  display: flex;
  gap: 12px;
  align-items: center;
}
</style>
