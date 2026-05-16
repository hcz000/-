<template>
  <div class="my-reports content">
    <div class="page-header content-header">
      <div>
        <h2>我的举报</h2>
        <p class="upload-hint">查看我提交的举报记录。</p>
      </div>
    </div>
    
    <el-card shadow="never" class="table-card surface-card">
      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        class="reports-table"
      >
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="targetType" label="举报类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.targetType === 'POST'" type="warning" size="small" round>帖子</el-tag>
            <el-tag v-else-if="row.targetType === 'COMMENT'" size="small" round>评论</el-tag>
            <el-tag v-else-if="row.targetType === 'USER'" type="danger" size="small" round>用户</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetId" label="目标 ID" width="100" align="center" />
        <el-table-column prop="reasonType" label="举报原因" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getReasonTypeColor(row.reasonType)" size="small" round>
              {{ getReasonTypeLabel(row.reasonType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reasonDetail" label="详细描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'PENDING'" type="warning" size="small" round>待处理</el-tag>
            <el-tag v-else-if="row.status === 'PROCESSING'" type="primary" size="small" round>处理中</el-tag>
            <el-tag v-else-if="row.status === 'RESOLVED'" type="success" size="small" round>已解决</el-tag>
            <el-tag v-else-if="row.status === 'REJECTED'" type="info" size="small" round>已拒绝</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="handleResult" label="处理结果" min-width="150" show-overflow-tooltip />
        <el-table-column prop="createTime" label="举报时间" width="160" align="center" />
      </el-table>
      
      <!-- 分页 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="pagination.total"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { http } from '../api/http'

const loading = ref(false)

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const tableData = ref([])

const fetchData = async () => {
  loading.value = true
  try {
    const data = await http.get('/report/my', {
      params: {
        pageNum: pagination.pageNum,
        pageSize: pagination.pageSize
      }
    })
    tableData.value = data?.records || data || []
    pagination.total = data?.total || 0
  } catch (error) {
    ElMessage.error(error.message || '获取举报列表失败')
    tableData.value = []
  } finally {
    loading.value = false
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

// 监听分页变化，重新获取数据
watch(
  () => [pagination.pageNum, pagination.pageSize],
  () => {
    fetchData()
  }
)

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.my-reports {
  padding: 0;
}

.page-header {
  margin-bottom: 32px;
}

.page-header h2 {
  font-size: 28px;
  color: var(--ink);
}

.page-header p {
  font-size: 15px;
  color: var(--muted);
}

.table-card {
  padding: 24px;
}

.reports-table {
  font-size: 14px;
  border-radius: var(--radius-md);
  overflow: hidden;
}

.reports-table :deep(.el-table__header-wrapper) th {
  background-color: var(--surface-muted);
  color: var(--ink);
  font-weight: 700;
  font-size: 13px;
}

.reports-table :deep(.el-table__cell) {
  padding: 12px 0;
}

.reports-table :deep(.el-table__body-wrapper) tr:hover > td {
  background-color: rgba(var(--accent-rgb), 0.05);
}

.pagination-container {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
}

.pagination-container :deep(.el-pagination__total),
.pagination-container :deep(.el-pagination__jump) {
  color: var(--muted);
}

.pagination-container :deep(.el-pager li) {
  border-radius: var(--radius-sm);
  transition: var(--transition-all);
}

.pagination-container :deep(.el-pager li.is-active) {
  background: var(--accent) !important;
  color: #fff !important;
}

.pagination-container :deep(.el-pager li:hover) {
  color: var(--accent);
  background: rgba(var(--accent-rgb), 0.1);
}

.pagination-container :deep(.el-select__wrapper) {
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.5);
  box-shadow: none;
  border: 1px solid var(--border);
  transition: var(--transition-all);
}

.pagination-container :deep(.el-select__wrapper.is-focused) {
  border-color: var(--accent);
  background: #fff;
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}
</style>
