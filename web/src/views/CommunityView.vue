<template>
  <section class="content">
    <div class="content-header">
      <div>
        <h2>社区</h2>
        <p class="upload-hint">发现最新共创话题与开放提案。</p>
      </div>
      <el-button type="primary" size="large" round @click="createDialogVisible = true">创建星球</el-button>
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
    <div v-else-if="planets.length === 0" class="surface-card" style="padding: 24px;">
      <el-empty description="暂无星球" />
    </div>
    <div v-else class="feed-grid">
      <el-card v-for="planet in planets" :key="planet.planetId" class="surface-card" shadow="never">
        <div class="planet-card-content">
          <h3>{{ planet.name }}</h3>
          <p class="upload-hint" style="margin-top: 8px;">{{ planet.description }}</p>
          <div class="planet-tags">
            <el-tag v-if="planet.category" type="success" effect="light" round>{{ planet.category }}</el-tag>
            <el-tag v-if="planet.master" effect="plain" round>主理人：{{ planet.master }}</el-tag>
            <el-tag type="info" effect="light" round>成员 {{ planet.memberCount ?? 0 }}</el-tag>
          </div>
          <div class="planet-actions">
            <el-button type="primary" round @click="goPlanet(planet.planetId)">
              查看星球
              <el-icon class="el-icon--right"><ArrowRight /></el-icon>
            </el-button>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 创建星球对话框 -->
    <el-dialog v-model="createDialogVisible" title="创建星球" width="500px" class="create-planet-dialog">
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="名称" required>
          <el-input v-model="createForm.name" placeholder="请输入星球名称" size="large" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入星球简介"
            size="large"
          />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="createForm.category" placeholder="请输入分类（可选）" size="large" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="createDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="createLoading" @click="handleCreatePlanet">
            创建
          </el-button>
        </span>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight } from '@element-plus/icons-vue'
import { http } from '../api/http'

const router = useRouter()
const planets = ref([])
const isLoading = ref(false)
const errorMessage = ref('')
const createDialogVisible = ref(false)
const createLoading = ref(false)
const createForm = ref({
  name: '',
  description: '',
  category: ''
})

const fetchPlanets = async () => {
  isLoading.value = true
  errorMessage.value = ''
  try {
    const data = await http.get('/planet?pageNum=1&pageSize=12')
    planets.value = Array.isArray(data) ? data : data?.records || []
  } catch (error) {
    errorMessage.value = error.message || '加载社区星球失败'
    planets.value = []
  } finally {
    isLoading.value = false
  }
}

const goPlanet = (planetId) => {
  if (planetId) {
    router.push(`/planets/${planetId}`)
  }
}

const handleCreatePlanet = async () => {
  if (!createForm.value.name.trim()) {
    ElMessage.warning('请输入星球名称')
    return
  }
  createLoading.value = true
  try {
    const data = await http.post('/planet', createForm.value)
    ElMessage.success('创建成功')
    createDialogVisible.value = false
    createForm.value = { name: '', description: '', category: '' }
    if (data?.planetId) {
      router.push(`/planets/${data.planetId}`)
    } else {
      fetchPlanets()
    }
  } catch (error) {
    ElMessage.error(error.message || '创建失败')
  } finally {
    createLoading.value = false
  }
}

onMounted(() => {
  fetchPlanets()
})
</script>

<style scoped>
.planet-card-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px;
}

.planet-card-content h3 {
  font-size: 18px;
  color: var(--ink);
}

.planet-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.planet-tags .el-tag {
  font-weight: 600;
}

.planet-actions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.planet-actions .el-button {
  font-weight: 600;
  height: 36px;
  padding: 0 16px;
  background: var(--accent);
  border: none;
  box-shadow: 0 4px 12px rgba(var(--accent-rgb), 0.2);
  transition: var(--transition-all);
}

.planet-actions .el-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(var(--accent-rgb), 0.3);
  background: var(--accent);
}

.create-planet-dialog :deep(.el-dialog) {
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: var(--shadow-lg);
}

.create-planet-dialog :deep(.el-dialog__header) {
  padding: 20px 24px;
  border-bottom: 1px solid var(--border);
  margin-right: 0;
}

.create-planet-dialog :deep(.el-dialog__title) {
  font-size: 20px;
  font-weight: 700;
  color: var(--ink);
}

.create-planet-dialog :deep(.el-dialog__body) {
  padding: 24px;
}

.create-planet-dialog :deep(.el-form-item__label) {
  font-weight: 600;
  color: var(--ink);
}

.create-planet-dialog :deep(.el-input__wrapper),
.create-planet-dialog :deep(.el-textarea__inner) {
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.5);
  box-shadow: none;
  border: 1px solid var(--border);
  transition: var(--transition-all);
}

.create-planet-dialog :deep(.el-input__wrapper.is-focus),
.create-planet-dialog :deep(.el-textarea__inner:focus) {
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
</style>
