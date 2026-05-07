<template>
  <header class="header">
    <div class="header-container">
      <div class="header-left">
        <button v-if="showBack" class="btn-back" @click="goBack">← 返回</button>
        <h1 class="header-title">{{ title }}</h1>
      </div>
      <div class="header-right">
        <slot name="right"></slot>
      </div>
    </div>
  </header>
</template>

<script setup>
/**
 * 顶部导航栏组件
 * - 左侧：可选返回按鈕 + 页面标题
 * - 右侧：预留命名插槽uff08负责<slot name="right">）
 * Props： title（页面标题）、showBack（是否显示返回按鈕）
 */
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

const props = defineProps({
  /** 页面标题，显示在导航栏中央 */
  title: {
    type: String,
    default: 'AI 互动智课',
  },
  /** 为 true 时展示返回按鈕（应用于设计详情、讲课等内层页面） */
  showBack: {
    type: Boolean,
    default: false,
  },
})

/** 执行浏览器历史负方向跳转 */
const goBack = () => {
  router.back()
}
</script>

<style scoped>
.header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 3rem;
  background: linear-gradient(135deg, var(--primary-color) 0%, var(--secondary-color) 100%);
  box-shadow: var(--shadow-md);
  z-index: 100;
}

.header-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 3rem;
  padding: 0 1rem;
  max-width: 100%;
  margin: 0 auto;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 1rem;
  flex: 1;
  min-width: 0;
}

.btn-back {
  padding: 0.5rem;
  background: rgba(255, 255, 255, 0.2);
  border: none;
  border-radius: var(--radius-md);
  color: white;
  cursor: pointer;
  font-size: var(--font-size-md);
  transition: all var(--transition-fast);
}

.btn-back:active {
  background: rgba(255, 255, 255, 0.3);
  transform: scale(0.95);
}

.header-title {
  font-size: var(--font-size-lg);
  color: white;
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

/* 移动端适配 */
@media (max-width: 480px) {
  .header {
    height: 3rem;
  }

  .header-container {
    padding: 0 0.75rem;
    height: 3rem;
  }

  .header-title {
    font-size: var(--font-size-md);
  }

  .btn-back {
    padding: 0.25rem 0.5rem;
    font-size: var(--font-size-sm);
  }
}
</style>
