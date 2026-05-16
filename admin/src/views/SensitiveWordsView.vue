<template>
  <div class="sensitive-words-management">
    <div class="page-header">
      <h2>敏感词管理</h2>
      <p>管理系统敏感词库，用于内容过滤和审核。</p>
    </div>
    
    <!-- 操作栏 -->
    <el-card shadow="never" class="toolbar-card">
      <el-button type="primary" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        添加敏感词
      </el-button>
      <el-button type="success" @click="handleLoad">
        <el-icon><Upload /></el-icon>
        批量导入
      </el-button>
    </el-card>
    
    <!-- 敏感词列表 -->
    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
      >
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="word" label="敏感词" min-width="200" />
        <el-table-column prop="createTime" label="添加时间" width="160" align="center" />
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="danger" link size="small" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    
    <!-- 添加对话框 -->
    <el-dialog
      v-model="dialogVisible"
      title="添加敏感词"
      width="400px"
      @close="handleDialogClose"
    >
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="80px">
        <el-form-item label="敏感词" prop="word">
          <el-input v-model="formData.word" placeholder="请输入敏感词" />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>
    
    <!-- 批量导入对话框 -->
    <el-dialog
      v-model="loadDialogVisible"
      title="批量导入敏感词"
      width="400px"
    >
      <el-form label-width="80px">
        <el-form-item label="文件路径">
          <el-input v-model="loadFilePath" placeholder="请输入敏感词文件路径" />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="loadDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="loadLoading" @click="handleLoadFile">
          导入
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Upload } from '@element-plus/icons-vue'
import { http } from '../api/http'

const loading = ref(false)
const submitLoading = ref(false)
const loadLoading = ref(false)
const dialogVisible = ref(false)
const loadDialogVisible = ref(false)
const formRef = ref(null)
const loadFilePath = ref('')

const tableData = ref([])

const formData = reactive({
  word: ''
})

const formRules = {
  word: [
    { required: true, message: '请输入敏感词', trigger: 'blur' }
  ]
}

const fetchWords = async () => {
  loading.value = true
  try {
    const data = await http.get('/sensitive/list')
    // 假设返回的是数组
    tableData.value = Array.isArray(data) ? data.map((word, index) => ({
      id: index + 1,
      word: word,
      createTime: '-'
    })) : []
  } catch (error) {
    ElMessage.error(error.message || '获取敏感词列表失败')
    tableData.value = []
  } finally {
    loading.value = false
  }
}

const handleAdd = () => {
  dialogVisible.value = true
}

const handleDelete = (row) => {
  ElMessageBox.confirm('确定要删除该敏感词吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await http.post('/sensitive/remove', null, {
        params: { word: row.word }
      })
      ElMessage.success('删除成功')
      fetchWords()
    } catch (error) {
      ElMessage.error(error.message || '删除失败')
    }
  }).catch(() => {})
}

const handleLoad = () => {
  loadDialogVisible.value = true
}

const handleLoadFile = async () => {
  if (!loadFilePath.value.trim()) {
    ElMessage.warning('请输入文件路径')
    return
  }
  
  loadLoading.value = true
  try {
    await http.post('/sensitive/load', null, {
      params: { filePath: loadFilePath.value }
    })
    ElMessage.success('导入成功')
    loadDialogVisible.value = false
    loadFilePath.value = ''
    fetchWords()
  } catch (error) {
    ElMessage.error(error.message || '导入失败')
  } finally {
    loadLoading.value = false
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitLoading.value = true
      try {
        await http.post('/sensitive/add', null, {
          params: { word: formData.word }
        })
        ElMessage.success('添加成功')
        dialogVisible.value = false
        fetchWords()
      } catch (error) {
        ElMessage.error(error.message || '添加失败')
      } finally {
        submitLoading.value = false
      }
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
  formData.word = ''
}

onMounted(() => {
  fetchWords()
})
</script>

<style scoped>
.sensitive-words-management {
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

.toolbar-card,
.table-card {
  margin-bottom: 20px;
}

.toolbar-card :deep(.el-card__body),
.table-card :deep(.el-card__body) {
  padding: 20px;
}

.toolbar-card {
  border-top: none;
}

:deep(.el-table) {
  font-size: 13px;
}

:deep(.el-table th) {
  background-color: #f5f7fa;
  color: #606266;
}
</style>
