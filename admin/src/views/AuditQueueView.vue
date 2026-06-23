<template>
  <section class="audit-queue">
    <div class="page-header">
      <h2>审核队列</h2>
      <p>集中处理待审核、已通过和已拒绝的帖子。</p>
    </div>

    <el-card shadow="never" class="toolbar">
      <el-radio-group v-model="auditStatus" @change="handleSearch">
        <el-radio-button :value="0">待审核</el-radio-button>
        <el-radio-button :value="1">已通过</el-radio-button>
        <el-radio-button :value="2">已拒绝</el-radio-button>
      </el-radio-group>
      <el-button type="primary" @click="fetchData">刷新</el-button>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="posts" border stripe>
        <el-table-column prop="postingsId" label="ID" width="120" />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="content" label="内容" min-width="240" show-overflow-tooltip />
        <el-table-column prop="type" label="类型" width="120" />
        <el-table-column prop="userId" label="作者ID" width="120" />
        <el-table-column prop="createTime" label="发布时间" width="180" />
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="row.auditStatus !== 1" link type="success" @click="audit(row, 1)">通过</el-button>
            <el-button v-if="row.auditStatus !== 2" link type="danger" @click="audit(row, 2)">拒绝</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="page.page"
          v-model:page-size="page.size"
          :total="page.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { http } from '../api/http'

const loading = ref(false)
const auditStatus = ref(0)
const posts = ref([])
const page = reactive({ page: 1, size: 10, total: 0 })

const fetchData = async () => {
  loading.value = true
  try {
    const data = await http.get('/admin/audit/posts', {
      params: { page: page.page, size: page.size, auditStatus: auditStatus.value }
    })
    posts.value = data?.content || data?.records || data || []
    page.total = data?.totalElements ?? data?.total ?? 0
  } catch (error) {
    ElMessage.error(error.message || '获取审核队列失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.page = 1
  fetchData()
}

const audit = async (row, status) => {
  const action = status === 1 ? '通过' : '拒绝'
  await ElMessageBox.confirm(`确定${action}这篇帖子吗？`, '审核确认', { type: 'warning' })
  try {
    await http.put(`/admin/posts/${row.postingsId}/audit`, null, { params: { auditStatus: status } })
    ElMessage.success(`${action}成功`)
    fetchData()
  } catch (error) {
    ElMessage.error(error.message || `${action}失败`)
  }
}

onMounted(fetchData)
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
  justify-content: space-between;
  align-items: center;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
