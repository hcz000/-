<template>
  <section class="content">
    <div class="search-command-center">
      <div class="search-container">
        <el-select
          v-model="searchType"
          class="search-type-select"
          placeholder="全部"
          size="large"
          clearable
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
          <el-option label="全部" value="" />
          <el-option label="帖子" value="post" />
          <el-option label="社区" value="planet" />
          <el-option label="用户" value="user" />
        </el-select>
        <el-input
          v-model="keyword"
          class="search-main-input"
          placeholder="搜索知识、星球或共创者..."
          size="large"
          clearable
        >
          <template #suffix>
            <span class="search-kbd">🔍</span>
          </template>
        </el-input>
      </div>
    </div>

    <div class="content-header">
      <div>
        <h2>首页 · 帖子推送</h2>
        <p class="upload-hint">为你精选正在热议的知识与项目。</p>
      </div>
      <div style="display: flex; gap: 12px; flex-wrap: wrap;">
        <el-radio-group
          v-model="feedMode"
          :disabled="isSearching || Boolean(selectedPlanetId)"
          @change="fetchFeed"
        >
          <el-radio-button value="a">综合</el-radio-button>
          <el-radio-button value="interest">兴趣</el-radio-button>
          <el-radio-button value="hot">热榜</el-radio-button>
          <el-radio-button value="planet">星球</el-radio-button>
          <el-radio-button value="friends">好友</el-radio-button>
        </el-radio-group>
        <el-select
          v-model="selectedPlanetId"
          placeholder="选择星球"
          style="min-width: 180px;"
          :loading="planetLoading"
          :disabled="planetLoading || planets.length === 0 || isSearching"
        >
          <el-option
            v-for="planet in planets"
            :key="planet.planetId"
            :label="planet.name"
            :value="planet.planetId"
          />
        </el-select>
        <el-button
          type="primary"
          :disabled="planets.length === 0"
          @click="handleCreatePost"
        >
          {{ planets.length === 0 ? '请先加入星球' : '发布新帖' }}
        </el-button>
      </div>
    </div>

    <el-card
      v-if="!isSearching"
      class="surface-card"
      shadow="never"
      style="padding: 12px 18px;"
    >
      <div style="display: flex; align-items: center; gap: 12px;">
        <el-tag type="success" effect="light">{{ activeFeedModeLabel }}</el-tag>
        <span class="upload-hint">{{ activeFeedModeHint }}</span>
      </div>
    </el-card>

    <el-alert
      v-if="planetError"
      type="error"
      :closable="false"
      show-icon
      :title="planetError"
    />
    <el-alert
      v-else-if="isSearching && searchError"
      type="error"
      :closable="false"
      show-icon
      :title="searchError"
    />
    <el-alert
      v-else-if="!isSearching && feedStore.error"
      type="error"
      :closable="false"
      show-icon
      :title="feedStore.error"
    />

    <div v-if="!isSearching && (planetLoading || feedStore.isLoading)">
      <el-skeleton :rows="4" animated />
    </div>
    <div v-else-if="!isSearching && feedStore.posts.length === 0">
      <el-empty description="暂无推送帖子" />
    </div>
    <div v-else-if="!isSearching" class="feed-grid">
      <PostCard v-for="post in feedStore.posts" :key="post.postingsId || post.id" :post="post" />
    </div>

    <div v-if="isSearching">
      <div v-if="searchLoading">
        <el-skeleton :rows="4" animated />
      </div>
      <div v-else-if="searchType && searchList.length === 0">
        <el-empty description="暂无搜索结果" />
      </div>
      <div v-else-if="searchType" class="feed-grid">
        <el-card
          v-for="item in searchList"
          :key="`${item.bizType}-${item.bizId}`"
          class="surface-card search-item-card"
          shadow="never"
          @contextmenu.prevent="openSearchMenu($event, item)"
        >
          <div class="search-card">
            <div class="search-header">
              <h3>{{ getSearchTitle(item) }}</h3>
              <el-tag size="small" effect="light">{{ getSearchTypeLabel(item.bizType) }}</el-tag>
            </div>
            <p v-if="getSearchMeta(item)" class="search-meta">{{ getSearchMeta(item) }}</p>
            <p class="search-content" v-html="highlightMentions(getSearchContent(item))"></p>
            <div class="search-actions">
              <el-button
                v-if="item.bizType === 'post'"
                type="primary"
                plain
                size="small"
                @click="goSearchTarget(item)"
              >
                查看帖子
              </el-button>
              <el-button
                v-else-if="item.bizType === 'planet'"
                type="primary"
                plain
                size="small"
                @click="goSearchTarget(item)"
              >
                查看社区
              </el-button>
            </div>
          </div>
        </el-card>
      </div>
      <div v-else>
        <div v-if="searchTotal === 0">
          <el-empty description="暂无搜索结果" />
        </div>
        <div v-else class="search-groups">
          <el-card
            v-for="section in searchSections"
            :key="section.key"
            class="surface-card"
            shadow="never"
            style="padding: 18px;"
          >
            <div class="search-section-header">
              <h3>{{ section.label }}</h3>
              <span class="upload-hint">共 {{ section.items.length }} 条</span>
            </div>
            <div class="feed-grid" style="margin-top: 12px;">
              <el-card
                v-for="item in section.items"
                :key="`${item.bizType}-${item.bizId}`"
                class="surface-card search-item-card"
                shadow="never"
                @contextmenu.prevent="openSearchMenu($event, item)"
              >
                <div class="search-card">
                  <div class="search-header">
                    <h3>{{ getSearchTitle(item) }}</h3>
                    <el-tag size="small" effect="light">{{ getSearchTypeLabel(item.bizType) }}</el-tag>
                  </div>
                  <p v-if="getSearchMeta(item)" class="search-meta">{{ getSearchMeta(item) }}</p>
                  <p class="search-content" v-html="highlightMentions(getSearchContent(item))"></p>
                  <div class="search-actions">
                    <el-button
                      v-if="item.bizType === 'post'"
                      type="primary"
                      plain
                      size="small"
                      @click="goSearchTarget(item)"
                    >
                      查看帖子
                    </el-button>
                    <el-button
                      v-else-if="item.bizType === 'planet'"
                      type="primary"
                      plain
                      size="small"
                      @click="goSearchTarget(item)"
                    >
                      查看社区
                    </el-button>
                    <el-button
                      v-else-if="item.bizType === 'user'"
                      type="primary"
                      plain
                      size="small"
                      @click="goSearchTarget(item)"
                    >
                      查看用户
                    </el-button>
                  </div>
                </div>
              </el-card>
            </div>
          </el-card>
        </div>
      </div>
    </div>

    <ContextMenu
      :visible="contextMenuVisible"
      :x="contextMenuX"
      :y="contextMenuY"
      :items="contextMenuItems"
      @action="handleContextAction"
      @close="contextMenuVisible = false"
    />
  </section>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { useFeedStore } from '../stores/useFeedStore'
import PostCard from '../components/PostCard.vue'
import ContextMenu from '../components/ContextMenu.vue'
import { http } from '../api/http'
import { highlightMentions } from '../utils/mentionFormatter'

const router = useRouter()
const feedStore = useFeedStore()
const keyword = ref('')
const planets = ref([])
const selectedPlanetId = ref('')
const feedMode = ref('a')
const planetLoading = ref(false)
const planetError = ref('')
const searchType = ref('')
const searchLoading = ref(false)
const searchError = ref('')
const searchResults = ref({
  posts: [],
  planets: [],
  users: []
})
const searchList = ref([])
const contextMenuVisible = ref(false)
const contextMenuX = ref(0)
const contextMenuY = ref(0)
const contextMenuItems = ref([])
const contextMenuTarget = ref(null)
let searchTimer = null

const isSearching = computed(() => keyword.value.trim().length > 0)
const searchSections = computed(() => {
  const sections = [
    { key: 'posts', label: '帖子', items: searchResults.value.posts },
    { key: 'planets', label: '社区', items: searchResults.value.planets },
    { key: 'users', label: '用户', items: searchResults.value.users }
  ]
  return sections.filter(section => section.items.length > 0)
})
const searchTotal = computed(() =>
  (searchResults.value.posts.length || 0)
  + (searchResults.value.planets.length || 0)
  + (searchResults.value.users.length || 0)
)
const feedModeMeta = {
  a: { label: '综合推送', hint: '兴趣优先，热榜补充，随机兜底。' },
  interest: { label: '兴趣推送', hint: '按你的兴趣模型分发内容。' },
  hot: { label: '热榜推送', hint: '来自 Redis 热榜的高热帖子。' },
  planet: { label: '星球推送', hint: '来自你已加入星球的最新帖子。' },
  friends: { label: '好友动态', hint: '来自好友发布的最新帖子。' }
}
const activeFeedModeLabel = computed(() => {
  if (selectedPlanetId.value) return '星球列表'
  return feedModeMeta[feedMode.value]?.label || feedModeMeta.a.label
})
const activeFeedModeHint = computed(() => {
  if (selectedPlanetId.value) return '正在查看选中星球里的公开帖子。'
  return feedModeMeta[feedMode.value]?.hint || feedModeMeta.a.hint
})

const fetchPlanets = async () => {
  planetLoading.value = true
  planetError.value = ''
  try {
    const data = await http.get('/planet/my?pageNum=1&pageSize=20')
    planets.value = Array.isArray(data) ? data : data?.records || []
    // 默认保持“推送流”，不自动切到某个星球列表，避免首屏数据被后续请求覆盖
    if (selectedPlanetId.value) {
      const exists = planets.value.some(p => String(p?.planetId) === String(selectedPlanetId.value))
      if (!exists) {
        selectedPlanetId.value = ''
      }
    }
  } catch (error) {
    planetError.value = error.message || '加载星球失败'
  } finally {
    planetLoading.value = false
  }
}

const resetSearchData = () => {
  searchResults.value = { posts: [], planets: [], users: [] }
  searchList.value = []
}

const normalizeList = (data) => (Array.isArray(data) ? data : data?.records || [])

const fetchSearch = async () => {
  const text = keyword.value.trim()
  if (!text) {
    resetSearchData()
    return
  }
  searchLoading.value = true
  searchError.value = ''
  try {
    const params = new URLSearchParams({
      keyword: text,
      pageNum: '1',
      pageSize: '10'
    })
    if (searchType.value) {
      const data = await http.get(`/search/biz/${searchType.value}?${params.toString()}`)
      searchList.value = normalizeList(data)
      searchResults.value = { posts: [], planets: [], users: [] }
    } else {
      const data = await http.get(`/search/async?${params.toString()}`)
      searchResults.value = {
        posts: normalizeList(data?.posts),
        planets: normalizeList(data?.planets),
        users: normalizeList(data?.users)
      }
      searchList.value = []
    }
  } catch (error) {
    searchError.value = error.message || '搜索失败'
    resetSearchData()
  } finally {
    searchLoading.value = false
  }
}

const fetchFeed = () => {
  if (keyword.value.trim()) {
    // 搜索模式
    return
  }
  if (selectedPlanetId.value) {
    // 选择星球时，获取该星球帖子
    feedStore.fetchPosts({
      planetId: selectedPlanetId.value,
      keyword: ''
    })
  } else {
    // 默认使用推送
    feedStore.fetchPush(feedMode.value)
  }
}

const triggerFetch = () => {
  if (isSearching.value) {
    fetchSearch()
  } else {
    searchError.value = ''
    resetSearchData()
    fetchFeed()
  }
}

onMounted(() => {
  fetchPlanets()
  // 默认加载推送帖子
  feedStore.fetchPush(feedMode.value)
})

watch([selectedPlanetId, keyword, searchType], () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    triggerFetch()
  }, 350)
})

const getSearchTypeLabel = (type) => {
  if (type === 'post') return '帖子'
  if (type === 'planet') return '社区'
  if (type === 'user') return '用户'
  return type || '结果'
}

const getSearchTitle = (item) => item?.title || item?.username || '未命名'

const getSearchContent = (item) => {
  if (item?.content) return item.content
  if (item?.bizType === 'user') {
    return item?.username ? `用户名：${item.username}` : '用户信息'
  }
  return '暂无简介'
}

const getSearchMeta = (item) => {
  const meta = []
  if (item?.planetCategory) meta.push(`分类 ${item.planetCategory}`)
  if (item?.planetId) meta.push(`社区 ${item.planetId}`)
  return meta.join(' · ')
}

const goSearchTarget = (item) => {
  if (!item?.bizId) return
  if (item.bizType === 'post') {
    router.push(`/posts/${item.bizId}`)
  } else if (item.bizType === 'planet') {
    router.push(`/planets/${item.bizId}`)
  } else if (item.bizType === 'user') {
    router.push(`/users/${item.bizId}`)
  }
}

const buildContextItems = (item) => {
  if (!item?.bizType) return []
  if (item.bizType === 'user') {
    return [
      { key: 'view-user', label: '查看用户' },
      { key: 'add-friend', label: '加好友' }
    ]
  }
  if (item.bizType === 'planet') {
    return [{ key: 'join-planet', label: '加入社区' }]
  }
  if (item.bizType === 'post') {
    return [{ key: 'comment', label: '评论' }]
  }
  return []
}

const openSearchMenu = (event, item) => {
  const items = buildContextItems(item)
  if (items.length === 0) return
  contextMenuX.value = event.clientX
  contextMenuY.value = event.clientY
  contextMenuItems.value = items
  contextMenuTarget.value = item
  contextMenuVisible.value = true
}

const handleContextAction = async (action) => {
  const target = contextMenuTarget.value
  if (!target?.bizId) return
  try {
    if (action.key === 'view-user') {
      router.push(`/users/${target.bizId}`)
    } else if (action.key === 'add-friend') {
      await http.post('/friends/request', {
        targetUserId: target.bizId,
        message: ''
      })
      ElMessage.success('好友申请已发送')
    } else if (action.key === 'join-planet') {
      await http.post(`/planet/${target.bizId}/join`, {})
      ElMessage.success('加入社区成功')
    } else if (action.key === 'comment') {
      router.push(`/posts/${target.bizId}`)
    }
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  }
}

// 跳转到选中的星球详情页发帖
const handleCreatePost = () => {
  const planetId = selectedPlanetId.value || planets.value[0]?.planetId
  if (!planetId) {
    ElMessage.warning('请先加入一个星球')
    router.push('/community')
    return
  }
  router.push(`/planets/${planetId}`)
}
</script>

<style scoped>
.content {
  padding-top: 100px;
}

.search-command-center {
  position: fixed;
  top: 32px;
  right: 32px;
  left: 344px; /* Sidebar width + gap */
  z-index: 1000;
  display: flex;
  justify-content: center;
  transition: var(--transition-all);
}

.search-container {
  width: 100%;
  display: flex;
  align-items: center;
  background: rgba(255, 255, 255, 0.5);
  backdrop-filter: blur(20px);
  border-radius: 20px;
  padding: 6px;
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: var(--shadow-lg);
}

.search-type-select {
  width: 130px;
  border-right: 1px solid var(--border);
}

.search-type-select :deep(.el-input__wrapper) {
  background: transparent !important;
  box-shadow: none !important;
}

.search-main-input {
  flex: 1;
}

.search-main-input :deep(.el-input__wrapper) {
  background: transparent !important;
  box-shadow: none !important;
}

.search-kbd {
  padding: 4px 8px;
  background: rgba(0, 0, 0, 0.05);
  border-radius: 6px;
  font-size: 11px;
  font-weight: 700;
  color: var(--muted);
  font-family: inherit;
}

.search-groups {
  display: grid;
  gap: 32px;
}

.search-item-card {
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.search-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.search-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.search-header h3 {
  font-size: 18px;
  color: var(--ink);
}

.search-meta {
  font-size: 12px;
  color: var(--accent-2);
  font-weight: 600;
}

.search-content {
  margin: 0;
  color: var(--muted);
  line-height: 1.6;
  font-size: 14px;
}

.search-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.search-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border);
}

@media (max-width: 1024px) {
  .search-command-center {
    left: 20px;
    right: 20px;
    top: 80px;
  }
}
</style>
