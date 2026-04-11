<template>
  <div class="courses-page">
    <h2>我的课程</h2>
    <p class="empty-tip">暂无课程，请先上传课件</p>
    <button class="btn btn-primary" @click="$router.push('/upload')">去上传</button>
  </div>
</template>

<script setup>
</script>

<style scoped>
.courses-page {
  padding: 2rem 1rem;
  text-align: center;
}

h2 {
  font-size: 1.25rem;
  color: var(--text-primary);
  margin-bottom: 1rem;
}

.empty-tip {
  color: var(--text-hint);
  font-size: 0.875rem;
  margin-bottom: 1.5rem;
}

.btn {
  padding: 0.6rem 1.5rem;
  border: none;
  border-radius: 0.5rem;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
}

.btn-primary {
  background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
  color: white;
}
</style>
