<template>
  <div class="post-management">
    <div class="page-header">
      <h2>帖子管理</h2>
      <p>管理系统所有帖子，包括审核、编辑和删除。</p>
    </div>
    
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="searchForm" size="default">
        <el-form-item label="帖子标题">
          <el-input v-model="searchForm.title" placeholder="请输入标题" clearable />
        </el-form-item>
        
        <el-form-item label="作者">
          <el-input v-model="searchForm.author" placeholder="请输入作者" clearable />
        </el-form-item>
        
        <el-form-item label="社区">
          <el-select v-model="searchForm.planetId" placeholder="选择社区" clearable>
            <el-option v-for="planet in planets" :key="planet.id" :label="planet.name" :value="planet.id" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
            <el-option label="已发布" value="1" />
            <el-option label="待审核" value="0" />
            <el-option label="已下架" value="-1" />
          </el-select>
        </el-form-item>
        
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
    
    <!-- 操作栏 -->
    <el-card shadow="never" class="toolbar-card">
      <el-button type="danger" :disabled="selectedRows.length === 0" @click="handleBatchDelete">
        <el-icon><Delete /></el-icon>
        批量删除
      </el-button>
      <el-button type="warning" :disabled="selectedRows.length === 0" @click="handleBatchAudit">
        <el-icon><Check /></el-icon>
        批量通过
      </el-button>
    </el-card>
    
    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column prop="postingsId" label="ID" width="80" align="center" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="图片" width="120" align="center">
          <template #default="{ row }">
            <el-image
              v-if="row.images && row.images.length > 0"
              :src="row.images[0]"
              fit="cover"
              style="width: 60px; height: 60px; border-radius: 4px"
              :preview-src-list="row.images"
              preview-teleported
            />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="author" label="作者" width="100" align="center" />
        <el-table-column prop="planetName" label="社区" width="100" align="center" />
        <el-table-column prop="views" label="浏览量" width="80" align="center" />
        <el-table-column prop="likes" label="点赞数" width="80" align="center" />
        <el-table-column prop="comments" label="评论数" width="80" align="center" />
        <el-table-column prop="auditStatus" label="审核状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.auditStatus === 1" type="success" size="small">已通过</el-tag>
            <el-tag v-else-if="row.auditStatus === 0" type="warning" size="small">待审核</el-tag>
            <el-tag v-else-if="row.auditStatus === 2" type="danger" size="small">已拒绝</el-tag>
            <el-tag v-else type="info" size="small">未知</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="发布时间" width="160" align="center" />
        <el-table-column label="操作" width="250" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleView(row)">
              查看
            </el-button>
            <el-button type="success" link size="small" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button
              v-if="row.auditStatus !== 1"
              type="success"
              link
              size="small"
              @click="handleAudit(row, 1)"
            >
              通过
            </el-button>
            <el-button
              v-if="row.auditStatus !== 2"
              type="danger"
              link
              size="small"
              @click="handleAudit(row, 2)"
            >
              拒绝
            </el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <!-- 分页 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="pagination.total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>
    
    <!-- 查看对话框 -->
    <el-dialog v-model="viewDialogVisible" title="帖子详情" width="800px">
      <div class="post-detail">
        <h3>{{ currentPost.title }}</h3>
        <div class="post-meta">
          <span>作者：{{ currentPost.author || currentPost.userId }}</span>
          <span>发布时间：{{ currentPost.createTime }}</span>
        </div>
        <el-divider />
        <div class="post-content">{{ currentPost.content }}</div>
        <div v-if="currentPost.images && currentPost.images.length > 0" class="images-section">
          <h4>图片：</h4>
          <div class="image-grid">
            <el-image
              v-for="(img, idx) in currentPost.images"
              :key="idx"
              :src="img"
              fit="cover"
              class="detail-image"
              :preview-src-list="currentPost.images"
              :initial-index="idx"
              preview-teleported
            />
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Delete, Check } from '@element-plus/icons-vue'
import { http } from '../api/http'

const loading = ref(false)
const viewDialogVisible = ref(false)
const selectedRows = ref([])
const currentPost = ref({})
const planets = ref([])

const searchForm = reactive({
  title: '',
  author: '',
  planetId: '',
  status: ''
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const tableData = ref([])

const fetchData = async () => {
  loading.value = true
  try {
    const params = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    if (searchForm.planetId) params.planetId = searchForm.planetId
    if (searchForm.status) params.status = searchForm.status
    if (searchForm.keyword) params.keyword = searchForm.keyword

    const data = await http.get('/admin/posts', { params })
    tableData.value = data?.records || data || []
    pagination.total = data?.total || 0
  } catch (error) {
    ElMessage.error(error.message || '获取帖子列表失败')
    tableData.value = []
  } finally {
    loading.value = false
  }
}

const fetchPlanets = async () => {
  try {
    const data = await http.get('/planet', {
      params: { pageNum: 1, pageSize: 100 }
    })
    const records = data?.records || []
    planets.value = records.map(p => ({ id: p.planetId, name: p.name }))
  } catch (error) {
    console.error('获取社区列表失败', error)
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  fetchData()
}

const handleReset = () => {
  searchForm.title = ''
  searchForm.author = ''
  searchForm.planetId = ''
  searchForm.status = ''
  handleSearch()
}

const handleView = (row) => {
  currentPost.value = { ...row }
  viewDialogVisible.value = true
}

const handleEdit = (row) => {
  // TODO: 编辑帖子
  ElMessage.info('编辑帖子功能待实现')
}

const handleAudit = (row, auditStatus) => {
  const action = auditStatus === 1 ? '通过' : '拒绝'
  ElMessageBox.confirm(`确定要${action}该帖子吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await http.put(`/admin/posts/${row.postingsId}/audit`, null, {
        params: { auditStatus }
      })
      ElMessage.success(`${action}成功`)
      fetchData()
    } catch (error) {
      ElMessage.error(error.message || `${action}失败`)
    }
  }).catch(() => {})
}

const handleDelete = (row) => {
  ElMessageBox.confirm('确定要删除该帖子吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await http.delete(`/admin/posts/${row.postingsId}`)
      ElMessage.success('删除成功')
      fetchData()
    } catch (error) {
      ElMessage.error(error.message || '删除失败')
    }
  }).catch(() => {})
}

const handleBatchDelete = () => {
  ElMessageBox.confirm(`确定要删除选中的 ${selectedRows.value.length} 个帖子吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const ids = selectedRows.value.map(item => item.postingsId)
      await http.delete('/admin/posts/batch', {
        data: ids
      })
      ElMessage.success('批量删除成功')
      fetchData()
    } catch (error) {
      ElMessage.error(error.message || '批量删除失败')
    }
  }).catch(() => {})
}

const handleBatchAudit = () => {
  ElMessageBox.confirm(`确定要通过选中的 ${selectedRows.value.length} 个帖子吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const ids = selectedRows.value.map(item => item.postingsId)
      await http.put('/admin/posts/batch/audit', ids, {
        params: { auditStatus: 1 }
      })
      ElMessage.success('批量通过成功')
      fetchData()
    } catch (error) {
      ElMessage.error(error.message || '批量通过失败')
    }
  }).catch(() => {})
}

const handleSelectionChange = (selection) => {
  selectedRows.value = selection
}

const handleSizeChange = () => {
  fetchData()
}

const handleCurrentChange = () => {
  fetchData()
}

onMounted(() => {
  fetchPlanets()
  fetchData()
})
</script>

<style scoped>
.post-management {
  padding: 0;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 20px;
  color: #303133;
  margin: 0 0 8px 0;
}

.page-header p {
  font-size: 14px;
  color: #909399;
  margin: 0;
}

.search-card,
.toolbar-card,
.table-card {
  margin-bottom: 20px;
}

.search-card :deep(.el-card__body),
.toolbar-card :deep(.el-card__body),
.table-card :deep(.el-card__body) {
  padding: 20px;
}

.toolbar-card {
  border-top: none;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

:deep(.el-table) {
  font-size: 13px;
}

:deep(.el-table th) {
  background-color: #f5f7fa;
  color: #606266;
}

.post-detail h3 {
  font-size: 18px;
  color: #303133;
  margin: 0 0 12px 0;
}

.post-meta {
  display: flex;
  gap: 20px;
  font-size: 14px;
  color: #909399;
  margin-bottom: 16px;
}

.post-content {
  font-size: 14px;
  color: #606266;
  line-height: 1.8;
  white-space: pre-wrap;
}

.images-section {
  margin-top: 20px;
}

.images-section h4 {
  font-size: 16px;
  color: #303133;
  margin-bottom: 12px;
}

.image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 12px;
}

.detail-image {
  width: 100%;
  height: 150px;
  border-radius: 8px;
  cursor: pointer;
}
</style>
