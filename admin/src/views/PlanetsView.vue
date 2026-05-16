<template>
  <div class="planet-management">
    <div class="page-header">
      <h2>社区管理</h2>
      <p>管理系统所有社区，包括创建、编辑和审核。</p>
    </div>
    
    <!-- 搜索栏 -->
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" :model="searchForm" size="default">
        <el-form-item label="社区名称">
          <el-input v-model="searchForm.name" placeholder="请输入社区名称" clearable />
        </el-form-item>
        
        <el-form-item label="分类">
          <el-select v-model="searchForm.category" placeholder="选择分类" clearable>
            <el-option v-for="cat in categories" :key="cat.value" :label="cat.label" :value="cat.value" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
            <el-option label="正常" value="1" />
            <el-option label="禁用" value="0" />
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
      <el-button type="primary" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        创建社区
      </el-button>
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
        <el-table-column prop="name" label="社区名称" min-width="150" />
        <el-table-column prop="category" label="分类" width="100" align="center" />
        <el-table-column prop="memberCount" label="成员数" width="90" align="center" />
        <el-table-column prop="postCount" label="帖子数" width="90" align="center" />
        <el-table-column prop="master" label="圈主" width="100" align="center" />
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="success" size="small">正常</el-tag>
            <el-tag v-else type="danger" size="small">禁用</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" align="center" />
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleView(row)">
              查看
            </el-button>
            <el-button type="success" link size="small" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button
              v-if="row.status === 1"
              type="warning"
              link
              size="small"
              @click="handleStatusChange(row, 0)"
            >
              禁用
            </el-button>
            <el-button
              v-else
              type="success"
              link
              size="small"
              @click="handleStatusChange(row, 1)"
            >
              启用
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
    
    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      @close="handleDialogClose"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="80px"
      >
        <el-form-item label="社区名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入社区名称" />
        </el-form-item>
        
        <el-form-item label="社区分类" prop="category">
          <el-select v-model="formData.category" placeholder="请选择分类">
            <el-option v-for="cat in categories" :key="cat.value" :label="cat.label" :value="cat.value" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="社区简介" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="4"
            placeholder="请输入社区简介"
          />
        </el-form-item>
        
        <el-form-item label="圈主" prop="master">
          <el-input v-model="formData.master" placeholder="请输入圈主用户名" />
        </el-form-item>
        
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio :label="1">正常</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Delete } from '@element-plus/icons-vue'
import { http } from '../api/http'

const loading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('创建社区')
const formRef = ref(null)
const selectedRows = ref([])
const categories = ref([
  { label: '技术讨论', value: '1' },
  { label: '生活分享', value: '2' },
  { label: '职场交流', value: '3' },
  { label: '学习成长', value: '4' }
])

const searchForm = reactive({
  name: '',
  category: '',
  status: ''
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const tableData = ref([])

const formData = reactive({
  id: null,
  name: '',
  category: '',
  description: '',
  master: '',
  status: 1
})

const formRules = {
  name: [
    { required: true, message: '请输入社区名称', trigger: 'blur' },
    { min: 2, max: 20, message: '社区名称长度在 2 到 20 个字符', trigger: 'blur' }
  ],
  category: [
    { required: true, message: '请选择社区分类', trigger: 'change' }
  ],
  description: [
    { required: true, message: '请输入社区简介', trigger: 'blur' }
  ],
  master: [
    { required: true, message: '请输入圈主用户名', trigger: 'blur' }
  ]
}

const fetchData = async () => {
  loading.value = true
  try {
    const params = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    if (searchForm.name) params.name = searchForm.name
    if (searchForm.category) params.category = searchForm.category
    if (searchForm.status) params.status = searchForm.status

    const data = await http.get('/admin/planets', { params })
    tableData.value = data?.records || data || []
    pagination.total = data?.total || 0
  } catch (error) {
    ElMessage.error(error.message || '获取社区列表失败')
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
  searchForm.name = ''
  searchForm.category = ''
  searchForm.status = ''
  handleSearch()
}

const handleAdd = () => {
  dialogTitle.value = '创建社区'
  dialogVisible.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = '编辑社区'
  Object.assign(formData, row)
  dialogVisible.value = true
}

const handleView = (row) => {
  // TODO: 查看详情
  ElMessage.info('查看社区详情功能待实现')
}

const handleDelete = (row) => {
  ElMessageBox.confirm('确定要删除该社区吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    // TODO: 调用删除 API
    ElMessage.success('删除成功')
    fetchData()
  }).catch(() => {})
}

const handleBatchDelete = () => {
  ElMessageBox.confirm(`确定要删除选中的 ${selectedRows.value.length} 个社区吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    // TODO: 调用批量删除 API
    ElMessage.success('批量删除成功')
    fetchData()
  }).catch(() => {})
}

const handleStatusChange = (row, status) => {
  const action = status === 1 ? '启用' : '禁用'
  ElMessageBox.confirm(`确定要${action}该社区吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    // TODO: 调用状态变更 API
    ElMessage.success(`${action}成功`)
    fetchData()
  }).catch(() => {})
}

const handleSelectionChange = (selection) => {
  selectedRows.value = selection
}

const handleSubmit = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitLoading.value = true
      try {
        // TODO: 调用新增/编辑 API
        setTimeout(() => {
          submitLoading.value = false
          ElMessage.success(formData.id ? '更新成功' : '创建成功')
          dialogVisible.value = false
          fetchData()
        }, 500)
      } catch (error) {
        submitLoading.value = false
        ElMessage.error(error.message || '操作失败')
      }
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
  formData.id = null
  formData.name = ''
  formData.category = ''
  formData.description = ''
  formData.master = ''
  formData.status = 1
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
.planet-management {
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
