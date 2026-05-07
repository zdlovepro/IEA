<template>
  <div class="layout">
    <Header />
    <main class="layout-main">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" :key="$route.fullPath" />
        </transition>
      </router-view>
    </main>
    <TabBar />
  </div>
</template>

<script setup>
// 根组件：提供三段式布局（固定顶栏 Header + 可滚动主内容区 + 固定底栏 TabBar）
// router-view 使用 fade 过渡动画，key 绑定完整路径以确保同名路由切换时也能触发动画
import Header from '@/components/Layout/Header.vue'
import TabBar from '@/components/Layout/TabBar.vue'
</script>

<style scoped>
.layout {
  width: 100%;
  height: 100vh;
  background-color: var(--bg-secondary);
}

.layout-main {
  padding-top: 3rem;
  padding-bottom: 3.25rem;
  height: 100vh;
  overflow-y: auto;
  overflow-x: hidden;
  -webkit-overflow-scrolling: touch;
}

/* 过渡动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity var(--transition-base);
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
