<template>
  <section class="content">
    <div class="content-header">
      <div>
        <h2>星球详情</h2>
        <p class="upload-hint">查看星球介绍与成员。</p>
      </div>
      <div style="display: flex; gap: 12px;">
        <el-button v-if="!isJoined" type="primary" round :loading="joinLoading" @click="handleJoin">
          <el-icon><Plus /></el-icon>
          加入星球
        </el-button>
        <el-tag v-else type="success" effect="light" size="large">已加入</el-tag>
        <el-button round @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
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

    <el-card
      v-else-if="planet"
      class="surface-card planet-header-card"
      shadow="never"
      @contextmenu.prevent="handlePlanetContextMenu"
    >
      <div class="planet-header-content">
        <div class="planet-info-main">
          <h2 style="margin: 0;">{{ planet.name }}</h2>
          <p class="upload-hint" style="margin: 8px 0 0;">
            {{ planet.description || '暂无简介' }}
          </p>
        </div>
        <div class="planet-meta-details upload-hint">
          <div>主理人：<span class="meta-value">{{ planet.master || '-' }}</span></div>
          <div>分类：<span class="meta-value">{{ planet.category || '-' }}</span></div>
        </div>
      </div>
    </el-card>

    <el-card class="surface-card" shadow="never" style="padding: 24px;">
      <div class="section-header">
        <h3>帖子</h3>
        <div class="section-actions">
          <span class="upload-hint">共 {{ posts.length }} 条</span>
          <el-button v-if="isJoined" type="primary" round @click="openPostDialog">
            <el-icon><Edit /></el-icon> 发帖
          </el-button>
          <span v-else class="upload-hint">加入星球后可发帖</span>
        </div>
      </div>

      <div v-if="postsLoading">
        <el-skeleton :rows="3" animated />
      </div>
      <div v-else-if="posts.length === 0" class="surface-card" style="padding: 24px;">
        <el-empty description="暂无帖子" />
      </div>
      <div v-else class="feed-grid" style="margin-top: 20px;">
        <PostCard
          v-for="post in posts"
          :key="post.postingsId || post.id"
          :post="post"
          @deleted="handlePostDeleted"
        />
      </div>
    </el-card>

    <el-card class="surface-card" shadow="never" style="padding: 24px;">
      <div class="section-header">
        <h3>成员</h3>
        <span class="upload-hint">共 {{ membersTotal }} 人</span>
      </div>

      <div v-if="membersLoading">
        <el-skeleton :rows="3" animated />
      </div>
      <div v-else-if="members.length === 0" class="surface-card" style="padding: 24px;">
        <el-empty description="暂无成员" />
      </div>
      <div v-else class="member-grid" style="margin-top: 20px;">
        <el-card v-for="member in members" :key="member.id" class="member-card surface-card" shadow="never">
          <div class="member-content">
            <el-avatar :size="52" :src="member.avatar" />
            <div>
              <div class="member-name">{{ member.username || member.email || `用户 ${member.id}` }}</div>
              <div class="upload-hint">{{ member.email }}</div>
            </div>
          </div>
        </el-card>
      </div>
    </el-card>

    <ContextMenu
      :visible="contextMenuVisible"
      :x="contextMenuX"
      :y="contextMenuY"
      @delete="handleDeletePlanet"
      @close="contextMenuVisible = false"
    />

    <!-- 发帖对话框 -->
    <el-dialog v-model="postDialogVisible" title="发布帖子" width="600px" class="create-post-dialog">
      <el-form :model="postForm" label-width="90px">
        <el-form-item label="帖子类型" required>
          <el-select v-model="postForm.type" placeholder="请选择帖子类型" size="large">
            <el-option
              v-for="t in postTypes"
              :key="t.value"
              :label="t.label"
              :value="t.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="postForm.title" placeholder="请输入帖子标题" size="large" />
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input
            v-model="postForm.content"
            type="textarea"
            :rows="6"
            placeholder="请输入帖子内容"
            size="large"
          />
        </el-form-item>
        <el-form-item label="图片">
          <el-upload
            action="/api/oss/upload"
            list-type="picture-card"
            :headers="uploadHeaders"
            :on-success="handleImageSuccess"
            :on-error="handleImageError"
            :on-remove="handleImageRemove"
            v-model:file-list="imageFileList"
            :limit="9"
            multiple
          >
            <el-icon><Plus /></el-icon>
          </el-upload>
          <div class="upload-tip">最多上传9张图片，支持jpg、png格式</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="postDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="postLoading" @click="handleCreatePost">
            发布
          </el-button>
        </span>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Edit, Plus } from '@element-plus/icons-vue'
import { http } from '../api/http'
import PostCard from '../components/PostCard.vue'
import ContextMenu from '../components/ContextMenu.vue'
import { useUserStore } from '../stores/useUserStore'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const planet = ref(null)
const members = ref([])
const membersTotal = ref(0)
const posts = ref([])
const isLoading = ref(false)
const membersLoading = ref(false)
const postsLoading = ref(false)
const errorMessage = ref('')
const contextMenuVisible = ref(false)
const contextMenuX = ref(0)
const contextMenuY = ref(0)
const postDialogVisible = ref(false)
const postLoading = ref(false)
const joinLoading = ref(false)
const isJoined = ref(false)
const postForm = ref({
  title: '',
  content: '',
  type: '闲聊生活', // 默认类型
  images: []
})

const imageFileList = ref([])
const uploadHeaders = computed(() => {
  const token = localStorage.getItem('kp:token') || sessionStorage.getItem('kp:token') || ''
  return token ? { Authorization: token } : {}
})

const postTypes = [
  { value: '闲聊生活', label: '闲聊生活' },
  { value: '运动健身', label: '运动健身' },
  { value: '美食探索', label: '美食探索' },
  { value: '游戏电竞', label: '游戏电竞' },
  { value: '影音书画', label: '影音书画' },
  { value: '摄影', label: '摄影' }
]

const planetId = computed(() => route.params.id)

const fetchPlanet = async () => {
  isLoading.value = true
  errorMessage.value = ''
  try {
    planet.value = await http.get(`/planet/${planetId.value}`)
    checkJoined()
  } catch (error) {
    errorMessage.value = error.message || '加载星球失败'
  } finally {
    isLoading.value = false
  }
}

const checkJoined = async () => {
  if (!userStore.isAuthenticated || !planet.value) {
    isJoined.value = false
    return
  }
  try {
    const data = await http.get('/planet/my?pageNum=1&pageSize=100')
    const joinedPlanets = Array.isArray(data) ? data : data?.records || []
    isJoined.value = joinedPlanets.some(p => String(p.planetId) === String(planetId.value))
  } catch (error) {
    isJoined.value = false
  }
}

const handleJoin = async () => {
  if (!userStore.isAuthenticated) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  joinLoading.value = true
  try {
    await http.post(`/planet/${planetId.value}/join`, {})
    ElMessage.success('加入成功')
    isJoined.value = true
    fetchMembers()
  } catch (error) {
    ElMessage.error(error.message || '加入失败')
  } finally {
    joinLoading.value = false
  }
}

const fetchMembers = async () => {
  membersLoading.value = true
  try {
    const data = await http.get(`/planet/${planetId.value}/members?pageNum=1&pageSize=20`)
    const list = Array.isArray(data) ? data : data?.records || []
    members.value = list
    membersTotal.value = data?.total || list.length
  } catch (error) {
    errorMessage.value = error.message || '加载成员失败'
    members.value = []
    membersTotal.value = 0
  } finally {
    membersLoading.value = false
  }
}

const fetchPosts = async () => {
  postsLoading.value = true
  try {
    const data = await http.get(`/postings?planetId=${planetId.value}&pageNum=1&pageSize=20`)
    posts.value = Array.isArray(data) ? data : data?.records || []
  } catch (error) {
    errorMessage.value = error.message || '加载帖子失败'
    posts.value = []
  } finally {
    postsLoading.value = false
  }
}

const goBack = () => {
  router.back()
}

const openPostDialog = () => {
  console.log('点击发帖按钮')
  console.log('isJoined:', isJoined.value)
  console.log('postDialogVisible 当前值:', postDialogVisible.value)
  postDialogVisible.value = true
  console.log('postDialogVisible 设置后:', postDialogVisible.value)
}

const handlePlanetContextMenu = (e) => {
  if (planet.value && planet.value.masterId === userStore.id) {
    e.preventDefault()
    contextMenuX.value = e.clientX
    contextMenuY.value = e.clientY
    contextMenuVisible.value = true
  }
}

const handleDeletePlanet = async () => {
  const id = planetId.value
  if (!id) return
  try {
    await ElMessageBox.confirm('确定要删除这个星球吗？删除后无法恢复。损坏宇宙！', '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await http.delete(`/planet/${id}`)
    ElMessage.success('星球已删除')
    router.push('/community')
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '删除失败')
    }
  } finally {
    contextMenuVisible.value = false
  }
}

const handlePostDeleted = (postId) => {
  posts.value = posts.value.filter(p => (p.postingsId || p.id) !== postId)
}

const handleCreatePost = async () => {
  if (!postForm.value.title.trim()) {
    ElMessage.warning('请输入帖子标题')
    return
  }
  if (!postForm.value.content.trim()) {
    ElMessage.warning('请输入帖子内容')
    return
  }
  if (!postForm.value.type) {
    ElMessage.warning('请选择帖子类型')
    return
  }
  console.log('原始 planetId (字符串):', planetId.value)
  postLoading.value = true
  try {
    const uploading = (imageFileList.value || []).some((file) => file?.status === 'uploading')
    if (uploading) {
      ElMessage.warning('图片仍在上传，请稍后再发布')
      return
    }

    const imageUrlsFromFileList = (imageFileList.value || [])
      .map((file) => extractUploadedUrl(file?.response || file?.url))
      .filter((url) => typeof url === 'string' && url.trim().length > 0)
    const mergedImages = Array.from(new Set([...(postForm.value.images || []), ...imageUrlsFromFileList]))

    const payload = {
      planetId: planetId.value,
      title: postForm.value.title,
      content: postForm.value.content,
      type: postForm.value.type,
      images: mergedImages
    }
    console.log('发送的请求体:', JSON.stringify(payload))
    const data = await http.post('/postings', payload)
    ElMessage.success('发布成功')
    postDialogVisible.value = false
    postForm.value = { title: '', content: '', type: '闲聊生活', images: [] }
    imageFileList.value = []
    if (data) {
      posts.value = [data, ...posts.value]
    }
  } catch (error) {
    ElMessage.error(error.message || '发布失败')
  } finally {
    postLoading.value = false
  }
}

const handleImageSuccess = (response, file, fileList) => {
  const url = extractUploadedUrl(response)
  if (url) {
    if (!postForm.value.images.includes(url)) {
      postForm.value.images.push(url)
    }
  } else {
    ElMessage.warning('图片上传返回格式异常，未获取到图片地址')
  }
}

const handleImageError = () => {
  ElMessage.error('图片上传失败，请检查登录状态或网络')
}

const handleImageRemove = (file, fileList) => {
  const removedUrl = extractUploadedUrl(file?.response)
  if (removedUrl) {
    postForm.value.images = postForm.value.images.filter(url => url !== removedUrl)
    return
  }
  const index = imageFileList.value.findIndex(f => f.uid === file.uid)
  if (index > -1) {
    postForm.value.images.splice(index, 1)
  }
}

const extractUploadedUrl = (resp) => {
  if (!resp) return ''

  if (typeof resp === 'string' && (resp.startsWith('http://') || resp.startsWith('https://') || resp.startsWith('//') || resp.startsWith('/'))) {
    return normalizeImageUrl(resp)
  }

  let payload = resp
  if (typeof payload === 'string') {
    try {
      payload = JSON.parse(payload)
    } catch {
      // raw string url
      return payload.startsWith('http://') || payload.startsWith('https://') ? payload : ''
    }
  }

  const candidates = [
    payload?.data,
    payload?.data?.url,
    payload?.url
  ]
  for (const c of candidates) {
    if (typeof c !== 'string') continue
    const u = normalizeImageUrl(c)
    if (u) return u
  }
  return ''
}

const normalizeImageUrl = (value) => {
  if (typeof value !== 'string') return ''
  const u = value.trim()
  if (!u) return ''
  if (u.startsWith('http://') || u.startsWith('https://')) return u
  if (u.startsWith('//')) return `${window.location.protocol}${u}`
  if (u.startsWith('/')) return `${window.location.origin}${u}`
  return `${window.location.origin}/${u}`
}

onMounted(() => {
  fetchPlanet()
  fetchPosts()
  fetchMembers()
})
</script>

<style scoped>
.planet-header-card {
  padding: 24px;
}

.planet-header-content {
  display: flex;
  justify-content: space-between;
  gap: 20px;
}

.planet-info-main h2 {
  font-size: 28px;
  color: var(--ink);
}

.planet-meta-details {
  text-align: right;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 14px;
}

.meta-value {
  font-weight: 600;
  color: var(--ink);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border);
  margin-bottom: 20px;
}

.section-header h3 {
  font-size: 20px;
  color: var(--ink);
}

.section-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.section-actions .el-button {
  font-weight: 600;
  height: 38px;
  padding: 0 16px;
  background: var(--accent);
  border: none;
  box-shadow: 0 4px 12px rgba(var(--accent-rgb), 0.2);
  transition: var(--transition-all);
}

.section-actions .el-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(var(--accent-rgb), 0.3);
  background: var(--accent);
}

.member-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.member-card {
  padding: 16px;
}

.member-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.member-name {
  font-weight: 700;
  font-size: 16px;
  color: var(--ink);
}

.create-post-dialog :deep(.el-dialog) {
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: var(--shadow-lg);
}

.create-post-dialog :deep(.el-dialog__header) {
  padding: 20px 24px;
  border-bottom: 1px solid var(--border);
  margin-right: 0;
}

.create-post-dialog :deep(.el-dialog__title) {
  font-size: 20px;
  font-weight: 700;
  color: var(--ink);
}

.create-post-dialog :deep(.el-dialog__body) {
  padding: 24px;
}

.create-post-dialog :deep(.el-form-item__label) {
  font-weight: 600;
  color: var(--ink);
}

.create-post-dialog :deep(.el-input__wrapper),
.create-post-dialog :deep(.el-textarea__inner) {
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.5);
  box-shadow: none;
  border: 1px solid var(--border);
  transition: var(--transition-all);
}

.create-post-dialog :deep(.el-input__wrapper.is-focus),
.create-post-dialog :deep(.el-textarea__inner:focus) {
  border-color: var(--accent);
  background: #fff;
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.dialog-footer {
  padding: 16px 24px 20px;
  border-top: 1px solid var(--border);
  text-align: right;
}

.dialog-footer .el-button {
  border-radius: var(--radius-md);
  font-weight: 600;
}

.dialog-footer .el-button--primary {
  background: var(--accent);
  border: none;
  box-shadow: 0 4px 12px rgba(var(--accent-rgb), 0.2);
}

.dialog-footer .el-button--primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(var(--accent-rgb), 0.3);
  background: var(--accent);
}

.upload-tip {
  font-size: 12px;
  color: var(--muted);
  margin-top: 8px;
}
</style>
