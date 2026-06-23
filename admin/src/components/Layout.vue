<template>
  <div class="admin-layout">
    <el-container>
      <!-- 侧边栏 -->
      <el-aside :width="isCollapse ? '64px' : '220px'" class="sidebar">
        <div class="logo">
          <el-icon><Monitor /></el-icon>
          <span v-show="!isCollapse">后台管理系统</span>
        </div>
        <el-menu
          :default-active="activeMenu"
          class="sidebar-menu"
          :collapse="isCollapse"
          background-color="#263238"
          text-color="#b0bec5"
          active-text-color="#409EFF"
          router
        >
          <el-menu-item index="/dashboard">
            <el-icon><DataAnalysis /></el-icon>
            <span v-show="!isCollapse">仪表盘</span>
          </el-menu-item>

          <el-menu-item index="/users">
            <el-icon><User /></el-icon>
            <span v-show="!isCollapse">用户列表</span>
          </el-menu-item>

          <el-menu-item index="/posts">
            <el-icon><Document /></el-icon>
            <span v-show="!isCollapse">帖子管理</span>
          </el-menu-item>

          <el-menu-item index="/comments">
            <el-icon><Comment /></el-icon>
            <span v-show="!isCollapse">评论管理</span>
          </el-menu-item>

          <el-menu-item index="/planets">
            <el-icon><Grid /></el-icon>
            <span v-show="!isCollapse">社区列表</span>
          </el-menu-item>

          <el-menu-item index="/reports">
            <el-icon><WarningFilled /></el-icon>
            <span v-show="!isCollapse">举报管理</span>
          </el-menu-item>

          <el-menu-item index="/sensitive-words">
            <el-icon><Lock /></el-icon>
            <span v-show="!isCollapse">敏感词管理</span>
          </el-menu-item>
          <el-menu-item index="/audit-queue">
            <el-icon><Tickets /></el-icon>
            <span v-show="!isCollapse">审核队列</span>
          </el-menu-item>

          <el-menu-item index="/system-config">
            <el-icon><Setting /></el-icon>
            <span v-show="!isCollapse">系统配置</span>
          </el-menu-item>

          <el-menu-item index="/push-status">
            <el-icon><TrendCharts /></el-icon>
            <span v-show="!isCollapse">推送状态</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      
      <!-- 主内容区 -->
      <el-container>
        <!-- 顶部导航 -->
        <el-header class="header">
          <div class="header-left">
            <el-icon class="collapse-icon" @click="toggleCollapse">
              <Fold v-if="!isCollapse" />
              <Expand v-else />
            </el-icon>
          </div>
          <div class="header-right">
            <el-dropdown>
              <div class="user-info">
                <el-avatar :size="32" :src="adminInfo?.avatar">
                  <el-icon><User /></el-icon>
                </el-avatar>
                <span class="username">{{ adminInfo?.username || '管理员' }}</span>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="handleLogout">
                    <el-icon><SwitchButton /></el-icon>
                    退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </el-header>
        
        <!-- 内容区域 -->
        <el-main class="main-content">
          <el-card class="content-card" shadow="never">
            <router-view />
          </el-card>
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import {
  Monitor,
  DataAnalysis,
  User,
  Document,
  Grid,
  Lock,
  WarningFilled,
  Tickets,
  Setting,
  TrendCharts,
  Fold,
  Expand,
  Comment,
  SwitchButton
} from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/useUserStore'
import { http } from '../api/http'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const isCollapse = ref(false)
const adminInfo = ref(null)

const activeMenu = computed(() => {
  return route.path
})

const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value
}

const fetchAdminInfo = async () => {
  try {
    const res = await http.get('/admin/info')
    adminInfo.value = res
  } catch (e) {
    adminInfo.value = null
  }
}

const handleLogout = async () => {
  try {
    // 调用后端退出登录接口
    await http.post('/user/logout')
    ElMessage.success('退出登录成功')
  } catch (error) {
    // 即使后端调用失败，也继续清除本地状态
    console.error('退出登录接口调用失败', error)
  }
  // 清除本地状态
  userStore.logout()
  router.push('/login')
}

onMounted(() => {
  fetchAdminInfo()
})
</script>

<style scoped>
.admin-layout {
  height: 100vh;
  overflow: hidden;
}

.el-container {
  height: 100%;
}

.sidebar {
  background-color: #263238;
  overflow-x: hidden;
  transition: width 0.3s;
  display: flex;
  flex-direction: column;
}

.sidebar :deep(.el-menu) {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  border-right: none;
  background-color: #263238;
}

.sidebar :deep(.el-menu-item) {
  display: flex;
  align-items: center;
  justify-content: center;
}

.logo {
  height: 60px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background-color: #1e272b;
  color: #ffffff;
  font-size: 16px;
  font-weight: 600;
  transition: width 0.3s;
  overflow: hidden;
}

.logo .el-icon {
  font-size: 24px;
  color: #409EFF;
  flex-shrink: 0;
}

.sidebar .el-menu-item:hover {
  background-color: #37474f !important;
}

.sidebar .el-menu-item.is-active {
  background-color: #409EFF !important;
  color: #ffffff !important;
}

/* 滚动条样式 */
.sidebar :deep(.el-menu)::-webkit-scrollbar {
  width: 6px;
}

.sidebar :deep(.el-menu)::-webkit-scrollbar-thumb {
  background-color: #37474f;
  border-radius: 3px;
}

.sidebar :deep(.el-menu)::-webkit-scrollbar-track {
  background-color: #263238;
}

.header {
  background-color: #ffffff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  height: 60px;
}

.header-left {
  display: flex;
  align-items: center;
}

.collapse-icon {
  font-size: 20px;
  cursor: pointer;
  color: #606266;
}

.collapse-icon:hover {
  color: #409EFF;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.notification-badge {
  cursor: pointer;
}

.notification-badge  {
  font-size: 20px;
  color: #606266;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.username {
  font-size: 14px;
  color: #606266;
}

.main-content {
  background-color: #f5f7fa;
  padding: 20px;
  overflow-y: auto;
}

.content-card {
  height: 100%;
}

.content-card :deep(.el-card__body) {
  padding: 20px;
}
</style>
