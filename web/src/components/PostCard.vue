<template>
  <el-card
    class="surface-card"
    shadow="never"
    @contextmenu.prevent="handleContextMenu"
  >
    <div class="post-card">
      <div class="post-header">
        <div class="post-title-area">
          <h3>{{ post.title }}</h3>
          <div v-if="metaText" class="post-meta">
            <span class="author-tag">{{ post.username || `用户 ${post.userId}` }}</span>
            <span class="dot">·</span>
            <span class="time-text">{{ post.createTime }}</span>
          </div>
        </div>
        <div v-if="likeCount != null" class="heat-badge">
          <el-icon><TrendCharts /></el-icon>
          <span>{{ likeCount }}</span>
        </div>
      </div>
      <p v-if="summary" class="post-summary" v-html="highlightMentions(summary)"></p>
      <div v-if="hasImages" class="post-images">
        <el-image
          v-for="(img, idx) in normalizedImages"
          :key="idx"
          :src="img"
          fit="cover"
          class="post-image-item"
          :preview-src-list="normalizedImages"
          :initial-index="idx"
          preview-teleported
        />
      </div>
      <div class="post-footer">
        <div class="stats-row">
          <span class="stat-item"><el-icon><Star /></el-icon> {{ likeCount || 0 }}</span>
          <span class="stat-item"><el-icon><ChatDotRound /></el-icon> {{ post.replyCount || 0 }}</span>
        </div>
        <div class="actions-row">
          <el-button class="action-btn-mini" circle :loading="likeLoading" @click="toggleLike">
            <HeartIcon :filled="liked" :class="{ 'is-liked': liked }" />
          </el-button>
          <el-button type="primary" class="detail-btn" @click="goDetail">
            进入星球
            <el-icon class="el-icon--right"><ArrowRight /></el-icon>
          </el-button>
        </div>
      </div>
    </div>
  </el-card>
  <ContextMenu
    :visible="contextMenuVisible"
    :x="contextMenuX"
    :y="contextMenuY"
    @delete="handleDelete"
    @close="contextMenuVisible = false"
  />
  <ReportDialog
    v-if="postId"
    v-model="reportDialogVisible"
    target-type="POST"
    :target-id="postId"
    @submitted="handleReportSubmitted"
  />
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowRight, ChatDotRound, Star, TrendCharts } from '@element-plus/icons-vue'
import { http } from '../api/http'
import HeartIcon from './HeartIcon.vue'
import ContextMenu from './ContextMenu.vue'
import ReportDialog from './ReportDialog.vue'
import { useUserStore } from '../stores/useUserStore'
import { highlightMentions } from '../utils/mentionFormatter'

const props = defineProps({
  post: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['deleted'])

const router = useRouter()
const userStore = useUserStore()
const liked = ref(false)
const likeLoading = ref(false)
const likeReady = ref(false)
const likeCount = ref(props.post?.likeCount ?? 0)
const contextMenuVisible = ref(false)
const contextMenuX = ref(0)
const contextMenuY = ref(0)
const reportDialogVisible = ref(false)
const postId = ref(null)

watch(
  () => props.post?.likeCount,
  (value) => {
    if (value != null) {
      likeCount.value = value
    }
  }
)

const summary = computed(() => {
  const content = props.post?.content || ''
  if (!content) return ''
  return content.length > 120 ? `${content.slice(0, 120)}...` : content
})

const metaText = computed(() => {
  const author = props.post?.username || (props.post?.userId ? `用户 ${props.post.userId}` : '')
  const time = props.post?.createTime || ''
  return [author, time].filter(Boolean).join(' · ')
})

const normalizedImages = computed(() => {
  if (!Array.isArray(props.post?.images)) return []
  return props.post.images
    .map((img) => normalizeImageUrl(img))
    .filter(Boolean)
})

const hasImages = computed(() => normalizedImages.value.length > 0)

const getId = () => props.post?.postingsId || props.post?.id

const ensureLikeInfo = async () => {
  if (likeReady.value) return
  const id = getId()
  if (!id) return
  try {
    const info = await http.get(`/postings/${id}/like`)
    liked.value = Boolean(info?.liked)
    if (info?.count != null) {
      likeCount.value = info.count
    }
  } catch (error) {
    // ignore to keep UI responsive
  } finally {
    likeReady.value = true
  }
}

const toggleLike = async () => {
  if (likeLoading.value) return
  const id = getId()
  if (!id) return
  likeLoading.value = true
  try {
    if (!likeReady.value) {
      await ensureLikeInfo()
    }
    const next = !liked.value
    const count = await http.post(`/postings/${id}/like?like=${next}`, {})
    liked.value = next
    likeCount.value = count
  } catch (error) {
    // ignore to keep UI responsive
  } finally {
    likeLoading.value = false
  }
}

const goDetail = () => {
  const id = props.post?.postingsId || props.post?.id
  if (id) {
    router.push(`/posts/${id}`)
  }
}

const showReport = () => {
  postId.value = props.post?.postingsId || props.post?.id
  if (postId.value) {
    reportDialogVisible.value = true
  }
}

const handleReportSubmitted = () => {
  // 举报提交成功后的处理，可以刷新列表等
}

const handleContextMenu = (e) => {
  const postUserId = props.post?.userId
  if (postUserId && postUserId === userStore.id) {
    contextMenuX.value = e.clientX
    contextMenuY.value = e.clientY
    contextMenuVisible.value = true
  }
}

const handleDelete = async () => {
  const id = getId()
  if (!id) return
  try {
    await ElMessageBox.confirm('确定要删除这篇帖子吗？', '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await http.delete(`/postings/${id}`)
    ElMessage.success('删除成功')
    emit('deleted', id)
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '删除失败')
    }
  }
}

const normalizeImageUrl = (value) => {
  if (typeof value !== 'string') return ''
  const v = value.trim()
  if (!v) return ''
  if (v.startsWith('http://') || v.startsWith('https://')) return v
  if (v.startsWith('//')) return `${window.location.protocol}${v}`
  if (v.startsWith('/')) return `${window.location.origin}${v}`
  return `${window.location.origin}/${v}`
}
</script>

<style scoped>
.post-card {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.post-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.post-title-area h3 {
  font-size: 18px;
  line-height: 1.4;
  color: var(--ink);
  margin-bottom: 6px;
}

.post-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--muted);
}

.author-tag {
  font-weight: 600;
  color: var(--accent-2);
}

.heat-badge {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: rgba(var(--accent-rgb), 0.1);
  color: var(--accent);
  border-radius: 99px;
  font-size: 12px;
  font-weight: 700;
}

.post-summary {
  margin: 0;
  color: var(--muted);
  line-height: 1.6;
  font-size: 14px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.post-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  padding-top: 16px;
  border-top: 1px solid var(--border);
}

.stats-row {
  display: flex;
  gap: 16px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--muted);
  font-weight: 500;
}

.actions-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.action-btn-mini {
  width: 36px;
  height: 36px;
  border: 1px solid var(--border);
  background: var(--surface-solid);
  transition: var(--transition-all);
}

.action-btn-mini:hover {
  border-color: var(--accent);
  color: var(--accent);
  transform: scale(1.1);
}

.detail-btn {
  border-radius: 12px;
  font-weight: 600;
  height: 36px;
  padding: 0 16px;
  background: var(--accent);
  border: none;
  box-shadow: 0 4px 12px rgba(var(--accent-rgb), 0.2);
}

.detail-btn:hover {
  background: var(--accent);
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(var(--accent-rgb), 0.3);
}

.is-liked {
  color: var(--accent);
  fill: var(--accent);
}

.post-images {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 8px;
  margin-top: 8px;
}

.post-image-item {
  width: 100%;
  height: 120px;
  border-radius: 8px;
  cursor: pointer;
  transition: transform 0.2s;
}

.post-image-item:hover {
  transform: scale(1.05);
}
</style>
