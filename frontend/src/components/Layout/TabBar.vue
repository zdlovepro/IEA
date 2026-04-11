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
import { useRoute } from 'vue-router'

const route = useRoute()

const tabs = [
  { path: '/', icon: '🏠', label: '首页' },
  { path: '/upload', icon: '📤', label: '上传' },
  { path: '/courses', icon: '📚', label: '课程' },
  { path: '/mine', icon: '👤', label: '我的' },
]

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
