<template>
  <div class="reports-management">
    <div class="page-header">
      <h2>举报管理</h2>
      <p>处理用户提交的举报信息。</p>
    </div>
    
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="searchForm" size="default">
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="选择状态" clearable>
            <el-option label="待处理" value="PENDING" />
            <el-option label="处理中" value="PROCESSING" />
            <el-option label="已解决" value="RESOLVED" />
            <el-option label="已拒绝" value="REJECTED" />
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
    
    <!-- 表格 -->
    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
      >
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="targetType" label="举报类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.targetType === 'POST'" type="warning" size="small">帖子</el-tag>
            <el-tag v-else-if="row.targetType === 'COMMENT'" size="small">评论</el-tag>
            <el-tag v-else-if="row.targetType === 'USER'" type="danger" size="small">用户</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetId" label="目标 ID" width="100" align="center" />
        <el-table-column prop="reasonType" label="举报原因" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getReasonTypeColor(row.reasonType)" size="small">
              {{ getReasonTypeLabel(row.reasonType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reasonDetail" label="详细描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="reporterId" label="举报人 ID" width="100" align="center" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'PENDING'" type="warning" size="small">待处理</el-tag>
            <el-tag v-else-if="row.status === 'PROCESSING'" type="primary" size="small">处理中</el-tag>
            <el-tag v-else-if="row.status === 'RESOLVED'" type="success" size="small">已解决</el-tag>
            <el-tag v-else-if="row.status === 'REJECTED'" type="info" size="small">已拒绝</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="handleResult" label="处理结果" min-width="150" show-overflow-tooltip />
        <el-table-column prop="createTime" label="举报时间" width="160" align="center" />
        <el-table-column label="操作" width="250" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.targetType === 'POST'"
              type="info"
              link
              size="small"
              @click="handleViewTarget(row)"
            >
              查看详情
            </el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              type="primary"
              link
              size="small"
              @click="handleProcess(row, 'PROCESSING')"
            >
              受理
            </el-button>
            <el-button
              v-if="row.status !== 'RESOLVED'"
              type="success"
              link
              size="small"
              @click="handleProcess(row, 'RESOLVED')"
            >
              解决
            </el-button>
            <el-button
              v-if="row.status !== 'REJECTED'"
              type="danger"
              link
              size="small"
              @click="handleProcess(row, 'REJECTED')"
            >
              拒绝
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
    
    <!-- 查看详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="被举报内容详情" width="800px">
      <div v-if="targetDetail" class="target-detail">
        <h3>{{ targetDetail.title }}</h3>
        <div class="meta-info">
          <span>作者ID: {{ targetDetail.userId }}</span>
          <span>发布时间: {{ targetDetail.createTime }}</span>
        </div>
        <el-divider />
        <div class="content-text">{{ targetDetail.content }}</div>
        <div v-if="targetDetail.images && targetDetail.images.length > 0" class="images-section">
          <h4>图片:</h4>
          <div class="image-grid">
            <el-image
              v-for="(img, idx) in targetDetail.images"
              :key="idx"
              :src="img"
              fit="cover"
              class="detail-image"
              :preview-src-list="targetDetail.images"
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
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { http } from '../api/http'

const loading = ref(false)
const detailDialogVisible = ref(false)
const targetDetail = ref(null)

const searchForm = reactive({
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
    if (searchForm.status) params.status = searchForm.status
    
    const data = await http.get('/report/list', { params })
    tableData.value = data?.records || data || []
    pagination.total = data?.total || 0
  } catch (error) {
    ElMessage.error(error.message || '获取举报列表失败')
    tableData.value = []
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  fetchData()
}

const handleReset = () => {
  searchForm.status = ''
  handleSearch()
}

const handleProcess = async (row, status) => {
  try {
    await http.post('/report/handle', null, {
      params: {
        reportId: row.id,
        status: status,
        handleResult: `管理员处理：${status}`
      }
    })
    ElMessage.success('处理成功')
    fetchData()
  } catch (error) {
    ElMessage.error(error.message || '处理失败')
  }
}

const handleViewTarget = async (row) => {
  if (row.targetType === 'POST') {
    try {
      const data = await http.get(`/postings/${row.targetId}`)
      targetDetail.value = data
      detailDialogVisible.value = true
    } catch (error) {
      ElMessage.error(error.message || '获取详情失败')
    }
  } else {
    ElMessage.info('暂不支持查看此类型详情')
  }
}

const getReasonTypeLabel = (type) => {
  const labels = {
    'SPAM': '垃圾广告',
    'ABUSE': '辱骂攻击',
    'ILLEGAL': '违法违规',
    'OTHER': '其他'
  }
  return labels[type] || type
}

const getReasonTypeColor = (type) => {
  const colors = {
    'SPAM': 'warning',
    'ABUSE': 'danger',
    'ILLEGAL': 'danger',
    'OTHER': 'info'
  }
  return colors[type] || 'info'
}

const handleSizeChange = () => {
  fetchData()
}

const handleCurrentChange = () => {
  fetchData()
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.reports-management {
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
.table-card {
  margin-bottom: 20px;
}

.search-card :deep(.el-card__body),
.table-card :deep(.el-card__body) {
  padding: 20px;
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

.target-detail h3 {
  font-size: 18px;
  color: #303133;
  margin: 0 0 12px 0;
}

.meta-info {
  display: flex;
  gap: 20px;
  font-size: 14px;
  color: #909399;
  margin-bottom: 16px;
}

.content-text {
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
