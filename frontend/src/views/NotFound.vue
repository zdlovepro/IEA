<template>
  <div class="not-found-page">
    <section class="page-shell page-section">
      <AppCard class="not-found-card" tone="glass">
        <span class="eyebrow">404</span>
        <h1 class="page-title">页面不存在</h1>
        <p class="page-description">
          你访问的页面可能已经被移动，或者当前链接无效。可以返回首页重新开始。
        </p>
        <div class="actions">
          <AppButton @click="router.push('/')">返回首页</AppButton>
          <AppButton variant="secondary" @click="router.push('/courses')">
            查看课程列表
          </AppButton>
        </div>
      </AppCard>
    </section>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import AppButton from '@/components/ui/AppButton.vue'
import AppCard from '@/components/ui/AppCard.vue'

const router = useRouter()
</script>

<style scoped>
.not-found-page {
  display: grid;
  place-items: center;
  min-height: calc(100vh - 5rem);
}

.not-found-card {
  max-width: 44rem;
  margin: 3rem auto;
  text-align: center;
}

.actions {
  display: flex;
  justify-content: center;
  gap: 0.85rem;
  margin-top: 1.6rem;
  flex-wrap: wrap;
}
</style>
