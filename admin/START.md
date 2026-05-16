# 后台管理系统前端 - 快速开始

## 项目已创建完成

后台管理系统前端界面已经创建完成，包含以下功能:

### 已完成的页面

1. **登录页面** (`/login`)
   - 用户名密码登录
   - 表单验证
   - 记住密码

2. **仪表盘** (`/dashboard`)
   - 数据统计卡片 (用户、帖子、社区、评论)
   - 增长趋势图表占位
   - 最新用户列表
   - 热门帖子列表

3. **用户管理** (`/users`)
   - 用户列表展示
   - 搜索/筛选
   - 新增/编辑用户
   - 用户状态管理
   - 批量删除

4. **帖子管理** (`/posts`)
   - 帖子列表展示
   - 搜索/筛选
   - 帖子审核 (通过/下架)
   - 查看详情
   - 批量操作

5. **社区管理** (`/planets`)
   - 社区列表展示
   - 搜索/筛选
   - 创建/编辑社区
   - 社区状态管理
   - 批量操作

6. **占位页面** (其他功能模块)
   - 用户角色
   - 评论管理
   - 分类管理
   - 通知管理
   - 好友管理
   - 搜索日志
   - 系统设置

## 启动步骤

### 1. 安装依赖

在项目根目录执行:

```bash
cd admin
npm install
```

### 2. 启动开发服务器

```bash
npm run dev
```

启动后访问：http://localhost:5173

### 3. 登录测试

默认使用 mock 数据，任意输入符合验证规则的用户名和密码即可登录。

例如:
- 用户名：admin
- 密码：123456

## 主要特点

### 样式设计
- ✅ 无圆角设计 (所有 Element Plus 组件)
- ✅ 稳重的商务色彩
- ✅ 深色侧边栏 (#263238)
- ✅ 白色内容区
- ✅ 简洁的表格和表单

### 技术特性
- ✅ Vue 3 Composition API
- ✅ Element Plus UI 组件库
- ✅ Vue Router 路由管理
- ✅ Pinia 状态管理
- ✅ Axios HTTP 客户端
- ✅ 路由守卫 (登录验证)
- ✅ 响应式布局

### 功能特性
- ✅ 侧边栏菜单导航
- ✅ 顶部导航栏
- ✅ 面包屑导航
- ✅ 表格分页
- ✅ 表单验证
- ✅ 批量操作
- ✅ 搜索筛选
- ✅ 对话框/模态框

## API 对接

### 修改 API 地址

编辑 `src/api/http.js`:

```javascript
const http = axios.create({
  baseURL: 'http://localhost:8080/api', // 修改为实际后端地址
  // ...
})
```

### 需要对接的接口

1. **登录接口**
   - POST /admin/login
   - 返回 token

2. **用户管理**
   - GET /admin/users/list - 用户列表
   - POST /admin/users - 新增用户
   - PUT /admin/users/{id} - 更新用户
   - DELETE /admin/users/{id} - 删除用户
   - PUT /admin/users/{id}/status - 变更状态

3. **帖子管理**
   - GET /admin/posts/list - 帖子列表
   - PUT /admin/posts/{id}/audit - 审核帖子
   - DELETE /admin/posts/{id} - 删除帖子
   - GET /admin/posts/{id} - 帖子详情

4. **社区管理**
   - GET /admin/planets/list - 社区列表
   - POST /admin/planets - 创建社区
   - PUT /admin/planets/{id} - 更新社区
   - DELETE /admin/planets/{id} - 删除社区

## 文件结构

```
admin/
├── src/
│   ├── api/
│   │   └── http.js           # HTTP 客户端配置
│   ├── components/
│   │   └── Layout.vue        # 主布局组件
│   ├── router/
│   │   └── index.js          # 路由配置
│   ├── stores/
│   │   └── useUserStore.js   # 用户状态管理
│   ├── views/
│   │   ├── LoginView.vue     # 登录页
│   │   ├── DashboardView.vue # 仪表盘
│   │   ├── UsersView.vue     # 用户管理
│   │   ├── PostsView.vue     # 帖子管理
│   │   ├── PlanetsView.vue   # 社区管理
│   │   └── PlaceholderView.vue # 占位页
│   ├── App.vue               # 根组件
│   └── main.js               # 入口文件
├── package.json
└── vite.config.js
```

## 下一步工作

1. 运行 `npm install` 安装依赖
2. 启动项目 `npm run dev`
3. 根据实际后端 API 调整接口地址
4. 对接登录接口
5. 对接各个管理模块的 API
6. 完善错误处理和权限控制
7. 添加图表库 (如 ECharts) 实现数据可视化

## 注意事项

- 所有 Element Plus 组件的圆角已在全局样式中移除
- Token 存储在 localStorage 中
- 路由已配置守卫，需要登录才能访问
- 当前使用 mock 数据，需要对接真实 API
- 表单验证规则可根据实际需求调整
