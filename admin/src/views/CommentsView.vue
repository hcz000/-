<template>
  <div class="comments-management">
    <div class="page-header">
      <h2>评论管理</h2>
      <p>管理系统所有一级评论，包括审核和删除。</p>
    </div>
    
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="searchForm" size="default">
        <el-form-item label="帖子 ID">
          <el-input v-model="searchForm.postId" placeholder="请输入帖子 ID" clearable />
        </el-form-item>
        
        <el-form-item label="用户 ID">
          <el-input v-model="searchForm.userId" placeholder="请输入用户 ID" clearable />
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
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="content" label="评论内容" min-width="300" show-overflow-tooltip />
        <el-table-column prop="userId" label="用户 ID" width="100" align="center" />
        <el-table-column prop="postId" label="帖子 ID" width="100" align="center" />
        <el-table-column prop="likeCount" label="点赞数" width="80" align="center" />
        <el-table-column prop="createTime" label="创建时间" width="160" align="center" />
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Delete } from '@element-plus/icons-vue'
import { http } from '../api/http'

const loading = ref(false)
const selectedRows = ref([])

const searchForm = reactive({
  postId: '',
  userId: ''
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
    if (searchForm.postId) params.postId = searchForm.postId
    if (searchForm.userId) params.userId = searchForm.userId
    
    const data = await http.get('/admin/comments', { params })
    tableData.value = data?.records || data || []
    pagination.total = data?.total || 0
  } catch (error) {
    ElMessage.error(error.message || '获取评论列表失败')
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
  searchForm.postId = ''
  searchForm.userId = ''
  handleSearch()
}

const handleDelete = (row) => {
  ElMessageBox.confirm('确定要删除该评论吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await http.delete(`/admin/comments/${row.id}`)
      ElMessage.success('删除成功')
      fetchData()
    } catch (error) {
      ElMessage.error(error.message || '删除失败')
    }
  }).catch(() => {})
}

const handleBatchDelete = () => {
  ElMessageBox.confirm(`确定要删除选中的 ${selectedRows.value.length} 个评论吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const ids = selectedRows.value.map(item => item.id)
      // 批量删除需要后端支持，这里逐个删除
      for (const id of ids) {
        await http.delete(`/admin/comments/${id}`)
      }
      ElMessage.success('批量删除成功')
      fetchData()
    } catch (error) {
      ElMessage.error(error.message || '批量删除失败')
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
  fetchData()
})
</script>

<style scoped>
.comments-management {
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
</style>
