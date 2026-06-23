<template>
  <section class="system-config">
    <div class="page-header">
      <h2>系统配置</h2>
      <p>调整推荐、热榜和审核相关运行参数。</p>
    </div>

    <el-card shadow="never" class="config-form">
      <el-form :inline="true" :model="form">
        <el-form-item label="配置键">
          <el-input v-model="form.key" placeholder="hot.like.weight" clearable />
        </el-form-item>
        <el-form-item label="配置值">
          <el-input v-model="form.value" placeholder="3.0" clearable />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" placeholder="可选" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="saveConfig">保存</el-button>
          <el-button @click="resetForm">清空</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="configs" border stripe>
        <el-table-column prop="configKey" label="配置键" min-width="180" />
        <el-table-column prop="configValue" label="配置值" min-width="160" />
        <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column prop="updateTime" label="更新时间" width="180" />
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="editConfig(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { http } from '../api/http'

const loading = ref(false)
const saving = ref(false)
const configs = ref([])
const form = reactive({ key: '', value: '', description: '' })

const fetchConfigs = async () => {
  loading.value = true
  try {
    configs.value = await http.get('/admin/configs') || []
  } catch (error) {
    ElMessage.error(error.message || '获取系统配置失败')
  } finally {
    loading.value = false
  }
}

const saveConfig = async () => {
  if (!form.key || form.value === '') {
    ElMessage.warning('请填写配置键和值')
    return
  }
  saving.value = true
  try {
    await http.put('/admin/configs', null, { params: { ...form } })
    ElMessage.success('保存成功')
    resetForm()
    fetchConfigs()
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const editConfig = (row) => {
  form.key = row.configKey
  form.value = row.configValue
  form.description = row.description || ''
}

const resetForm = () => {
  form.key = ''
  form.value = ''
  form.description = ''
}

onMounted(fetchConfigs)
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

.config-form {
  margin-bottom: 20px;
}
</style>
