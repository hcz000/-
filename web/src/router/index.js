import { createRouter, createWebHistory } from 'vue-router'

import FriendsView from '../views/FriendsView.vue'
import CommunityView from '../views/CommunityView.vue'
import NotificationsView from '../views/NotificationsView.vue'
import LikedPostsView from '../views/LikedPostsView.vue'
import MyReportsView from '../views/MyReportsView.vue'
import UserDetailView from '../views/UserDetailView.vue'

import PostDetailView from '../views/PostDetailView.vue'
import PlanetDetailView from '../views/PlanetDetailView.vue'
import { useUserStore } from '../stores/useUserStore'

// 需要登录才能访问的页面
const authRequiredRoutes = ['/friends', '/notifications', '/likes', '/my-reports']

const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('../views/HomeView.vue'),
    meta: { public: true }
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { public: true, layout: 'auth' }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('../views/RegisterView.vue'),
    meta: { public: true, layout: 'auth' }
  },
  { path: '/posts/:id', name: 'post-detail', component: PostDetailView, meta: { public: true } },
  { path: '/planets/:id', name: 'planet-detail', component: PlanetDetailView, meta: { public: true } },
  { path: '/users/:id', name: 'user-detail', component: UserDetailView, meta: { public: true } },
  { path: '/friends', name: 'friends', component: FriendsView },
  { path: '/community', name: 'community', component: CommunityView, meta: { public: true } },
  { path: '/notifications', name: 'notifications', component: NotificationsView },
  { path: '/likes', name: 'likes', component: LikedPostsView },
  { path: '/my-reports', name: 'my-reports', component: MyReportsView }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  }
})

router.beforeEach(async (to) => {
  const userStore = useUserStore()
  const isAuthRoute = to.meta.layout === 'auth'
  const isPublicRoute = to.meta.public === true

  // 尝试获取用户信息（不强制要求）
  if (!userStore.profileLoaded) {
    await userStore.fetchProfile()
  }

  // 登录/注册页面：已登录用户跳转到首页
  if (isAuthRoute) {
    if (userStore.isAuthenticated) {
      return { path: '/', replace: true }
    }
    return true
  }

  // 公开页面：允许任何人访问
  if (isPublicRoute) {
    return true
  }

  // 需要登录的页面：未登录跳转到登录页
  if (!userStore.isAuthenticated) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  return true
})

export default router



