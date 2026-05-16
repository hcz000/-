# 知识星球后台管理系统

基于 Vue 3 + Element Plus 的后台管理系统。

## 功能模块

- **仪表盘**: 系统数据概览
- **用户管理**: 用户列表、角色管理
- **内容管理**: 帖子管理、评论管理
- **社区管理**: 社区列表、分类管理
- **通知管理**: 系统通知
- **好友管理**: 好友关系管理
- **搜索日志**: 搜索记录查询
- **系统设置**: 系统参数配置

## 技术栈

- Vue 3.5.30
- Element Plus 2.7.6
- Vue Router 4.3.2
- Pinia 2.1.7
- Axios 1.6.8

## 运行项目

### 安装依赖

```bash
npm install
```

### 启动开发服务器

```bash
npm run dev
```

### 构建生产版本

```bash
npm run build
```

### 预览生产构建

```bash
npm run preview
```

## 项目结构

```
admin/
├── public/              # 静态资源
├── src/
│   ├── api/            # API 接口
│   │   └── http.js     # axios 封装
│   ├── assets/         # 资源文件
│   ├── components/     # 公共组件
│   │   └── Layout.vue  # 主布局组件
│   ├── router/         # 路由配置
│   │   └── index.js
│   ├── stores/         # Pinia 状态管理
│   │   └── useUserStore.js
│   ├── views/          # 页面组件
│   │   ├── LoginView.vue      # 登录页
│   │   ├── DashboardView.vue  # 仪表盘
│   │   ├── UsersView.vue      # 用户管理
│   │   ├── PostsView.vue      # 帖子管理
│   │   ├── PlanetsView.vue    # 社区管理
│   │   └── PlaceholderView.vue # 占位页面
│   ├── App.vue         # 根组件
│   └── main.js         # 入口文件
├── index.html
├── package.json
└── vite.config.js
```

## 页面说明

### 登录页
- 用户名/密码登录
- 记住密码功能
- 表单验证

### 仪表盘
- 数据统计卡片
- 用户增长趋势
- 帖子发布统计
- 最新用户列表
- 热门帖子列表

### 用户管理
- 用户列表展示
- 搜索/筛选功能
- 新增/编辑用户
- 用户状态管理
- 批量操作

### 帖子管理
- 帖子列表展示
- 帖子审核功能
- 帖子编辑/删除
- 批量操作

### 社区管理
- 社区列表展示
- 创建/编辑社区
- 社区状态管理
- 批量操作

## 样式特点

- 采用简洁的商务风格
- 无圆角设计
- 色彩稳重
- 表格和表单优化
- 响应式布局

## 待对接功能

1. 登录接口
2. 用户管理接口
3. 帖子管理接口
4. 社区管理接口
5. 统计图表数据
6. 权限管理

## 注意事项

- API 基础地址在 `src/api/http.js` 中配置
- 登录 token 存储在 localStorage 中
- 路由守卫已配置，需要登录才能访问后台页面
- 所有 Element Plus 组件的圆角已全局移除
