<template>
  <nav class="tab-bar">
    <router-link
      v-for="tab in tabs"
      :key="tab.path"
      :to="tab.path"
      class="tab-item"
      :class="{ active: isActive(tab.path) }"
    >
      <span class="tab-icon">{{ tab.icon }}</span>
      <span class="tab-label">{{ tab.label }}</span>
    </router-link>
  </nav>
</template>

<script setup>
/**
 * 底部导航栏组件
 * 根据 tabs 配置渲染固定项，每项匹配当前路由后高亮
 */
import { useRoute } from 'vue-router'

const route = useRoute()

/** 底部导航项配置，顺序即显示顺序 */
const tabs = [
  { path: '/', icon: '🏠', label: '首页' },
  { path: '/upload', icon: '📤', label: '上传' },
  { path: '/courses', icon: '📚', label: '课程' },
  { path: '/mine', icon: '👤', label: '我的' },
]

/**
 * 判断导航项是否处于激活状态
 * 首页使用精确匹配，其他页面使用路径前缀匹配（支持子路由高亮）
 */
const isActive = (path) => {
  if (path === '/') return route.path === '/'
  return route.path.startsWith(path)
}
</script>

<style scoped>
.tab-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 3.25rem;
  background: #fff;
  border-top: 1px solid var(--border-color);
  display: flex;
  justify-content: space-around;
  align-items: center;
  z-index: 100;
  padding-bottom: env(safe-area-inset-bottom, 0);
}

.tab-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  height: 100%;
  text-decoration: none;
  color: var(--text-hint);
  transition: color 0.2s;
  gap: 2px;
}

.tab-item.active {
  color: var(--primary-color);
}

.tab-icon {
  font-size: 1.25rem;
  line-height: 1;
}

.tab-label {
  font-size: 0.625rem;
  line-height: 1;
}
</style>
