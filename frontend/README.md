# SoftDesign 前端项目

Vue 3 + Vite H5 应用

## 项目结构

```
src/
├── assets/        # 静态资源（图片、样式等）
├── components/    # 组件
├── views/         # 页面
├── router/        # 路由配置
├── stores/        # Pinia 状态管理
├── App.vue        # 根组件
└── main.js        # 应用入口

```

## 快速开始

### 安装依赖
```bash
npm install
# 或
yarn install
# 或
pnpm install
```

### 开发服务器
```bash
npm run dev
```

服务器运行在 http://localhost:5173

### 构建生产版本
```bash
npm run build
```

### 预览生产构建
```bash
npm run preview
```

### 代码检查和格式化
```bash
npm run lint
```

## 技术栈

- **框架**: Vue 3
- **构建工具**: Vite
- **状态管理**: Pinia
- **HTTP 客户端**: Axios
- **路由**: Vue Router
- **代码规范**: ESLint + Prettier

## 开发指南

### 添加新页面

1. 在 `src/views` 创建 `.vue` 文件
2. 在 `src/router/index.js` 中添加路由配置

### 添加新组件

在 `src/components` 创建组件文件，在其他组件或页面中导入使用

### 使用状态管理

在 `src/stores` 定义 store，在组件中使用 Pinia

### 样式

- 全局样式: `src/assets/styles/main.css`
- 组件样式: 在 `.vue` 文件的 `<style scoped>` 中编写
