import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/LoginView.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: () => import('../components/Layout.vue'),
    redirect: '/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/DashboardView.vue'),
        meta: { title: '仪表盘' }
      },
      {
        path: 'users',
        name: 'Users',
        component: () => import('../views/UsersView.vue'),
        meta: { title: '用户列表' }
      },
      {
        path: 'posts',
        name: 'Posts',
        component: () => import('../views/PostsView.vue'),
        meta: { title: '帖子管理' }
      },
      {
        path: 'comments',
        name: 'Comments',
        component: () => import('../views/CommentsView.vue'),
        meta: { title: '评论管理' }
      },
      {
        path: 'planets',
        name: 'Planets',
        component: () => import('../views/PlanetsView.vue'),
        meta: { title: '社区列表' }
      },
      {
        path: 'reports',
        name: 'Reports',
        component: () => import('../views/ReportsView.vue'),
        meta: { title: '举报管理' }
      },
      {
        path: 'sensitive-words',
        name: 'SensitiveWords',
        component: () => import('../views/SensitiveWordsView.vue'),
        meta: { title: '敏感词管理' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - 后台管理系统` : '后台管理系统'
  
  const token = localStorage.getItem('adminToken')
  
  if (to.path === '/login') {
    next()
  } else {
    if (token) {
      next()
    } else {
      next('/login')
    }
  }
})

export default router
