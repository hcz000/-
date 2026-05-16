<template>
  <div class="dashboard">
    <div class="page-header">
      <h2>仪表盘</h2>
      <p>欢迎使用后台管理系统，这是系统的概览数据。</p>
    </div>
    
    <!-- 数据卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon user">
              <el-icon><User /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalUsers.toLocaleString() }}</div>
              <div class="stat-label">总用户数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon post">
              <el-icon><Document /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalPosts.toLocaleString() }}</div>
              <div class="stat-label">总帖子数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon planet">
              <el-icon><Grid /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalPlanets.toLocaleString() }}</div>
              <div class="stat-label">活跃社区</div>
            </div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon comment">
              <el-icon><ChatDotRound /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalComments.toLocaleString() }}</div>
              <div class="stat-label">总评论数</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    
    <!-- 图表区域 -->
    <el-row :gutter="20" class="chart-row">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>用户增长趋势</span>
            </div>
          </template>
          <div class="chart-placeholder">
            <el-empty description="用户增长趋势图表" />
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>帖子发布统计</span>
            </div>
          </template>
          <div class="chart-placeholder">
            <el-empty description="帖子发布统计图表" />
          </div>
        </el-card>
      </el-col>
    </el-row>
    
    <!-- 最新动态 -->
    <el-row :gutter="20">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>最新用户</span>
            </div>
          </template>
          <el-table :data="recentUsers" style="width: 100%" size="small">
            <el-table-column prop="username" label="用户名" />
            <el-table-column prop="email" label="邮箱" />
            <el-table-column prop="registerTime" label="注册时间" />
          </el-table>
        </el-card>
      </el-col>
      
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>热门帖子</span>
            </div>
          </template>
          <el-table :data="hotPosts" style="width: 100%" size="small">
            <el-table-column prop="title" label="标题" show-overflow-tooltip />
            <el-table-column prop="views" label="浏览量" width="80" />
            <el-table-column prop="likes" label="点赞" width="80" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { User, Document, Grid, ChatDotRound } from '@element-plus/icons-vue'
import { http } from '../api/http'
import { ElMessage } from 'element-plus'

const stats = reactive({
  totalUsers: 0,
  totalPosts: 0,
  totalPlanets: 0,
  totalComments: 0,
  totalReports: 0
})

const loading = ref(false)
const recentUsers = ref([])
const hotPosts = ref([])

const fetchStats = async () => {
  loading.value = true
  try {
    const data = await http.get('/admin/stats/overview')
    Object.assign(stats, data)
  } catch (error) {
    ElMessage.error('获取统计数据失败')
  } finally {
    loading.value = false
  }
}

const fetchRecentUsers = async () => {
  try {
    const data = await http.get('/admin/users', {
      params: { pageNum: 1, pageSize: 4 }
    })
    recentUsers.value = data?.records || data || []
  } catch (error) {
    console.error('获取最新用户失败', error)
  }
}

const fetchHotPosts = async () => {
  try {
    const data = await http.get('/admin/posts', {
      params: { pageNum: 1, pageSize: 4 }
    })
    hotPosts.value = data?.records || data || []
  } catch (error) {
    console.error('获取热门帖子失败', error)
  }
}

onMounted(() => {
  fetchStats()
  fetchRecentUsers()
  fetchHotPosts()
})
</script>

<style scoped>
.dashboard {
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

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  text-align: left;
}

.stat-card :deep(.el-card__body) {
  padding: 20px;
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.stat-icon {
  width: 60px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  color: #ffffff;
}

.stat-icon.user {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.stat-icon.post {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
}

.stat-icon.planet {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
}

.stat-icon.comment {
  background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
  line-height: 1;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 14px;
  color: #909399;
}

.stat-trend {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.trend-text {
  color: #909399;
}

.chart-row {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-header span {
  font-weight: 600;
  color: #303133;
}

.chart-placeholder {
  height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}

:deep(.el-table) {
  font-size: 13px;
}

:deep(.el-table th) {
  background-color: #f5f7fa;
  color: #606266;
}
</style>
