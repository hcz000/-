<template>
  <section class="content">
    <div class="content-header">
      <div>
        <h2>帖子详情</h2>
        <p class="upload-hint">查看帖子内容与评论。</p>
      </div>
      <el-button round @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        返回
      </el-button>
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

    <el-card v-else-if="post" class="surface-card post-detail-card" shadow="never" style="padding: 24px;">
      <div class="post-detail-header">
        <div class="post-detail-info">
          <h2 style="margin: 0;">{{ post.title }}</h2>
          <p class="upload-hint" style="margin: 8px 0 0;">
            {{ metaText }}
          </p>
        </div>
        <div class="post-detail-actions">
          <el-button
            :type="liked ? 'primary' : 'default'"
            :loading="likeLoading"
            circle
            class="like-button"
            @click="toggleLike"
          >
            <HeartIcon :filled="liked" :class="{ 'is-liked': liked }" />
          </el-button>
          <el-button
            type="danger"
            plain
            size="small"
            round
            @click="showPostReport"
          >
            <el-icon><Warning /></el-icon>
            举报
          </el-button>
        </div>
      </div>
      <div
        class="post-content-body upload-hint"
        v-html="highlightMentions(post.content, '暂无内容')"
      ></div>
      <div class="post-stats-footer">
        <span class="stat-item"><el-icon><Star /></el-icon> {{ post.likeCount || 0 }}</span>
        <span class="stat-item"><el-icon><ChatDotRound /></el-icon> {{ post.replyCount || 0 }}</span>
      </div>
    </el-card>

    <el-card class="surface-card" shadow="never" style="padding: 24px;">
      <div class="section-header">
        <h3>评论</h3>
        <span class="upload-hint">共 {{ post?.replyCount || 0 }} 条</span>
      </div>

      <div class="comment-compose">
        <el-input
          v-model="newComment"
          type="textarea"
          :rows="4"
          placeholder="写下你的评论..."
          size="large"
        />
        <div class="comment-actions">
          <el-button type="primary" round :loading="commentSubmitting" @click="submitComment">
            发布评论
            <el-icon class="el-icon--right"><Promotion /></el-icon>
          </el-button>
        </div>
      </div>

      <div v-if="commentsLoading">
        <el-skeleton :rows="4" animated />
      </div>
      <div v-else-if="primaryComments.length === 0" class="surface-card" style="padding: 24px;">
        <el-empty description="暂无评论" />
      </div>
      <div v-else class="comments-list" style="margin-top: 20px;">
        <div
          v-for="comment in primaryComments"
          :key="comment.id"
          class="comment-card surface-card"
          shadow="never"
          @contextmenu.prevent="handleCommentContextMenu($event, comment)"
        >
          <div class="comment-header">
            <div class="comment-author"><strong>用户 {{ comment.userId }}</strong></div>
            <div class="comment-meta-time">
              <span class="upload-hint">{{ comment.createTime }}</span>
              <el-tag v-if="comment.firstComment" type="success" size="small" round>首评</el-tag>
            </div>
          </div>
          <p class="comment-content-text" v-html="highlightMentions(comment.content)"></p>
          <div class="comment-footer">
            <div class="comment-actions-row">
              <el-button
                text
                :type="comment.liked ? 'primary' : 'default'"
                :loading="comment.likeLoading"
                class="like-button"
                @click="togglePrimaryLike(comment)"
              >
                <HeartIcon :filled="comment.liked" :class="{ 'is-liked': comment.liked }" />
                <span>{{ comment.likeCount || 0 }}</span>
              </el-button>
              <span class="stat-item">回复 {{ comment.repliesTotal || 0 }}</span>
            </div>
            <div class="comment-reply-actions">
              <el-button
                text
                type="primary"
                round
                size="small"
                @click="toggleReplies(comment.id)"
              >
                {{ expanded[comment.id] ? '收起回复' : '查看回复' }}
              </el-button>
              <el-button
                text
                type="primary"
                round
                size="small"
                @click="toggleReplyBox(comment.id)"
              >
                {{ replyOpen[comment.id] ? '取消回复' : '回复' }}
              </el-button>
              <el-button
                text
                type="danger"
                round
                size="small"
                @click="showCommentReport(comment)"
              >
                <el-icon><Warning /></el-icon>
                举报
              </el-button>
            </div>
          </div>

          <div v-if="replyOpen[comment.id]" class="reply-compose">
            <el-input
              v-model="replyDrafts[comment.id]"
              type="textarea"
              :rows="3"
              placeholder="回复这条评论..."
              size="large"
            />
            <div class="reply-actions">
              <el-button
                type="primary"
                round
                :loading="replySubmitting[comment.id]"
                @click="submitReply(comment)"
              >
                发送回复
                <el-icon class="el-icon--right"><Promotion /></el-icon>
              </el-button>
            </div>
          </div>

          <div v-if="expanded[comment.id]" class="reply-list">
            <div v-if="secondaryLoading[comment.id]">
              <el-skeleton :rows="2" animated />
            </div>
            <div v-else-if="!secondaryMap[comment.id] || secondaryMap[comment.id].length === 0" class="surface-card" style="padding: 16px;">
              <el-empty description="暂无回复" />
            </div>
            <div v-else class="reply-items">
              <div
                v-for="reply in secondaryMap[comment.id]"
                :key="reply.id"
                class="reply-item"
                @contextmenu.prevent="handleReplyContextMenu($event, reply)"
              >
                <div class="reply-item-header">
                  <strong>用户 {{ reply.userId }}</strong>
                  <span class="upload-hint" style="margin-left: 8px;">{{ reply.createTime }}</span>
                </div>
                <p class="reply-item-content" v-html="highlightMentions(reply.content)"></p>
                <div class="reply-item-actions">
                  <el-button
                    text
                    size="small"
                    :type="reply.liked ? 'primary' : 'default'"
                    :loading="reply.likeLoading"
                    class="like-button"
                    @click="toggleSecondaryLike(comment.id, reply)"
                  >
                    <HeartIcon :filled="reply.liked" :class="{ 'is-liked': reply.liked }" />
                    <span>{{ reply.likeCount || 0 }}</span>
                  </el-button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <ContextMenu
      :visible="contextMenuVisible"
      :x="contextMenuX"
      :y="contextMenuY"
      @delete="handleContextMenuDelete"
      @close="contextMenuVisible = false"
    />
    
    <!-- 帖子举报对话框 -->
    <ReportDialog
      v-model="postReportVisible"
      target-type="POST"
      :target-id="postId"
      @submitted="handleReportSubmitted"
    />
    
    <!-- 评论举报对话框 -->
    <ReportDialog
      v-model="commentReportVisible"
      target-type="COMMENT"
      :target-id="reportTargetId"
      @submitted="handleReportSubmitted"
    />
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Warning, Star, ChatDotRound, Promotion } from '@element-plus/icons-vue'
import { http } from '../api/http'
import HeartIcon from '../components/HeartIcon.vue'
import ContextMenu from '../components/ContextMenu.vue'
import ReportDialog from '../components/ReportDialog.vue'
import { useUserStore } from '../stores/useUserStore'
import { highlightMentions } from '../utils/mentionFormatter'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const post = ref(null)
const isLoading = ref(false)
const commentsLoading = ref(false)
const errorMessage = ref('')
const liked = ref(false)
const likeLoading = ref(false)
const primaryComments = ref([])
const expanded = reactive({})
const secondaryMap = reactive({})
const secondaryLoading = reactive({})
const newComment = ref('')
const commentSubmitting = ref(false)
const replyOpen = reactive({})
const replyDrafts = reactive({})
const replySubmitting = reactive({})
const contextMenuVisible = ref(false)
const contextMenuX = ref(0)
const contextMenuY = ref(0)
const contextMenuTarget = ref(null)
const postReportVisible = ref(false)
const commentReportVisible = ref(false)
const reportTargetId = ref(null)

const postId = computed(() => route.params.id)

const metaText = computed(() => {
  if (!post.value) return ''
  const author = post.value.userId ? `用户 ${post.value.userId}` : ''
  const time = post.value.createTime || ''
  return [author, time].filter(Boolean).join(' · ')
})

const fetchPost = async () => {
  isLoading.value = true
  errorMessage.value = ''
  try {
    post.value = await http.get(`/postings/${postId.value}`)
    const likeInfo = await http.get(`/postings/${postId.value}/like`)
    liked.value = Boolean(likeInfo?.liked)
    if (post.value) {
      post.value.likeCount = likeInfo?.count ?? post.value.likeCount
    }
  } catch (error) {
    errorMessage.value = error.message || '加载帖子失败'
  } finally {
    isLoading.value = false
  }
}

const fetchComments = async () => {
  commentsLoading.value = true
  try {
    const data = await http.get(`/primary-comment0?postingsId=${postId.value}&pageNum=1&pageSize=20`)
    primaryComments.value = Array.isArray(data) ? data : data?.records || []
    await hydratePrimaryLikes(primaryComments.value)
  } catch (error) {
    errorMessage.value = error.message || '加载评论失败'
  } finally {
    commentsLoading.value = false
  }
}

const toggleLike = async () => {
  if (likeLoading.value || !post.value) return
  likeLoading.value = true
  try {
    const next = !liked.value
    const count = await http.post(`/postings/${postId.value}/like?like=${next}`, {})
    liked.value = next
    post.value.likeCount = count
  } catch (error) {
    errorMessage.value = error.message || '操作失败'
  } finally {
    likeLoading.value = false
  }
}

const toggleReplies = async (commentId) => {
  expanded[commentId] = !expanded[commentId]
  if (!expanded[commentId]) return
  if (secondaryMap[commentId]) return

  secondaryLoading[commentId] = true
  try {
    const data = await http.get(`/secondary-comment0?primaryCommentId=${commentId}&pageNum=1&pageSize=20`)
    secondaryMap[commentId] = Array.isArray(data) ? data : data?.records || []
    await hydrateSecondaryLikes(commentId, secondaryMap[commentId])
  } catch (error) {
    errorMessage.value = error.message || '加载回复失败'
  } finally {
    secondaryLoading[commentId] = false
  }
}

const hydratePrimaryLikes = async (comments) => {
  if (!Array.isArray(comments) || comments.length === 0) return
  const tasks = comments.map(async (comment) => {
    if (!comment?.id) return
    if (comment.liked != null && comment.likeCount != null) return
    try {
      const info = await http.get(`/primary-comment0/${comment.id}/like`)
      comment.liked = Boolean(info?.liked)
      if (info?.count != null) {
        comment.likeCount = info.count
      }
    } catch (error) {
      comment.liked = comment.liked ?? false
      comment.likeCount = comment.likeCount ?? 0
    }
  })
  await Promise.all(tasks)
}

const hydrateSecondaryLikes = async (commentId, replies) => {
  if (!commentId || !Array.isArray(replies) || replies.length === 0) return
  const tasks = replies.map(async (reply) => {
    if (!reply?.id) return
    if (reply.liked != null && reply.likeCount != null) return
    try {
      const info = await http.get(`/secondary-comment0/${reply.id}/like`)
      reply.liked = Boolean(info?.liked)
      if (info?.count != null) {
        reply.likeCount = info.count
      }
    } catch (error) {
      reply.liked = reply.liked ?? false
      reply.likeCount = reply.likeCount ?? 0
    }
  })
  await Promise.all(tasks)
}

const togglePrimaryLike = async (comment) => {
  if (!comment?.id || comment.likeLoading) return
  comment.likeLoading = true
  try {
    if (comment.liked == null) {
      const info = await http.get(`/primary-comment0/${comment.id}/like`)
      comment.liked = Boolean(info?.liked)
      if (info?.count != null) {
        comment.likeCount = info.count
      }
    }
    const next = !comment.liked
    const count = await http.post(`/primary-comment0/${comment.id}/like?like=${next}`, {})
    comment.liked = next
    comment.likeCount = count
  } catch (error) {
    errorMessage.value = error.message || '点赞失败'
  } finally {
    comment.likeLoading = false
  }
}

const toggleSecondaryLike = async (commentId, reply) => {
  if (!reply?.id || reply.likeLoading) return
  reply.likeLoading = true
  try {
    if (reply.liked == null) {
      const info = await http.get(`/secondary-comment0/${reply.id}/like`)
      reply.liked = Boolean(info?.liked)
      if (info?.count != null) {
        reply.likeCount = info.count
      }
    }
    const next = !reply.liked
    const count = await http.post(`/secondary-comment0/${reply.id}/like?like=${next}`, {})
    reply.liked = next
    reply.likeCount = count
  } catch (error) {
    errorMessage.value = error.message || '点赞失败'
  } finally {
    reply.likeLoading = false
  }
}

const toggleReplyBox = (commentId) => {
  replyOpen[commentId] = !replyOpen[commentId]
  if (!replyOpen[commentId]) {
    replyDrafts[commentId] = ''
  }
}

const showPostReport = () => {
  postReportVisible.value = true
}

const showCommentReport = (comment) => {
  reportTargetId.value = comment.id
  commentReportVisible.value = true
}

const handleReportSubmitted = () => {
  // 举报提交成功，可以刷新数据或不做任何操作
}

// 右键菜单相关方法
const handleCommentContextMenu = (event, comment) => {
  contextMenuTarget.value = { type: 'comment', data: comment }
  contextMenuX.value = event.clientX
  contextMenuY.value = event.clientY
  contextMenuVisible.value = true
}

const handleReplyContextMenu = (event, reply) => {
  contextMenuTarget.value = { type: 'reply', data: reply }
  contextMenuX.value = event.clientX
  contextMenuY.value = event.clientY
  contextMenuVisible.value = true
}

const handleContextMenuDelete = async () => {
  if (!contextMenuTarget.value) return
  const { type, data } = contextMenuTarget.value
  contextMenuVisible.value = false

  try {
    if (type === 'comment') {
      await http.delete(`/primary-comment0/${data.id}`)
      primaryComments.value = primaryComments.value.filter(c => c.id !== data.id)
      ElMessage.success('评论已删除')
    } else if (type === 'reply') {
      await http.delete(`/secondary-comment0/${data.id}`)
      // 从对应的二级评论列表中移除
      for (const key in secondaryMap) {
        secondaryMap[key] = secondaryMap[key].filter(r => r.id !== data.id)
      }
      ElMessage.success('回复已删除')
    }
  } catch (error) {
    ElMessage.error(error.message || '删除失败')
  }
}

const goBack = () => {
  router.back()
}

const submitReply = async (comment) => {
  const commentId = comment?.id
  if (!commentId || replySubmitting[commentId]) return
  const content = (replyDrafts[commentId] || '').trim()
  if (!content) {
    errorMessage.value = '回复内容不能为空'
    return
  }
  replySubmitting[commentId] = true
  try {
    const payload = {
      primaryCommentId: commentId,
      postingsId: postId.value,  // 直接传字符串，避免 Number() 精度丢失
      content
    }
    const data = await http.post('/secondary-comment0', payload)
    if (data) {
      if (!expanded[commentId]) {
        expanded[commentId] = true
      }
      if (!secondaryMap[commentId]) {
        secondaryMap[commentId] = []
      }
      secondaryMap[commentId] = [...secondaryMap[commentId], data]
      await hydrateSecondaryLikes(commentId, secondaryMap[commentId])
      replyDrafts[commentId] = ''
      replyOpen[commentId] = false
    }
  } catch (error) {
    errorMessage.value = error.message || '回复失败'
  } finally {
    replySubmitting[commentId] = false
  }
}

const submitComment = async () => {
  if (commentSubmitting.value) return
  const content = newComment.value.trim()
  if (!content) {
    errorMessage.value = '评论内容不能为空'
    return
  }
  commentSubmitting.value = true
  try {
    const payload = {
      postingsId: postId.value,  // 直接传字符串，避免 Number() 精度丢失
      content
    }
    const data = await http.post('/primary-comment0', payload)
    if (data) {
      const exists = primaryComments.value.some((item) => item?.id === data.id)
      if (!exists) {
        primaryComments.value = [data, ...primaryComments.value]
        await hydratePrimaryLikes(primaryComments.value)
        if (post.value) {
          post.value.replyCount = (post.value.replyCount || 0) + 1
        }
      }
      newComment.value = ''
    }
  } catch (error) {
    errorMessage.value = error.message || '评论失败'
  } finally {
    commentSubmitting.value = false
  }
}



onMounted(() => {
  fetchPost()
  fetchComments()
})
</script>

<style scoped>
.post-detail-card {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.post-detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.post-detail-info h2 {
  font-size: 28px;
  color: var(--ink);
  line-height: 1.2;
}

.post-detail-info p {
  font-size: 14px;
  color: var(--muted);
  margin-top: 8px;
}

.post-detail-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.like-button {
  width: 40px;
  height: 40px;
  border: 1px solid var(--border);
  background: var(--surface-solid);
  transition: var(--transition-all);
}

.like-button:hover {
  border-color: var(--accent);
  transform: scale(1.1);
}

.like-button.is-liked {
  color: var(--accent);
  fill: var(--accent);
}

.post-content-body {
  line-height: 1.7;
  font-size: 15px;
  color: var(--muted);
  white-space: pre-wrap;
}

.post-stats-footer {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--border);
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: var(--muted);
  font-weight: 500;
}

.comment-compose {
  margin-top: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.comment-actions {
  display: flex;
  justify-content: flex-end;
}

.comment-actions .el-button {
  font-weight: 600;
  height: 40px;
  padding: 0 20px;
  background: var(--accent);
  border: none;
  box-shadow: 0 4px 12px rgba(var(--accent-rgb), 0.2);
  transition: var(--transition-all);
}

.comment-actions .el-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(var(--accent-rgb), 0.3);
  background: var(--accent);
}

.comments-list {
  display: grid;
  gap: 20px;
}

.comment-card {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.comment-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
}

.comment-author strong {
  font-size: 16px;
  color: var(--ink);
}

.comment-meta-time {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--muted);
}

.comment-content-text {
  margin: 8px 0 0;
  white-space: pre-wrap;
  line-height: 1.7;
  font-size: 15px;
  color: var(--muted);
}

.comment-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--border);
}

.comment-actions-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.comment-reply-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.comment-reply-actions .el-button {
  font-weight: 600;
  font-size: 13px;
}

.reply-compose {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.reply-actions {
  display: flex;
  justify-content: flex-end;
}

.reply-list {
  margin-top: 16px;
  padding-left: 24px;
  border-left: 2px solid rgba(var(--accent-rgb), 0.1);
  display: grid;
  gap: 16px;
}

.reply-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.reply-item-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.reply-item-content {
  margin: 4px 0 0;
  white-space: pre-wrap;
  line-height: 1.6;
  font-size: 14px;
  color: var(--muted);
}

.reply-item-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
</style>
