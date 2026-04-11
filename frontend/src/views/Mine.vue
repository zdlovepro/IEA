<template>
  <div class="mine-page">
    <div class="avatar-section">
      <div class="avatar">👤</div>
      <p class="username">未登录</p>
    </div>

    <div class="menu-list">
      <div class="menu-item">
        <span>学习记录</span>
        <span class="arrow">›</span>
      </div>
      <div class="menu-item">
        <span>问答历史</span>
        <span class="arrow">›</span>
      </div>
      <div class="menu-item">
        <span>系统设置</span>
        <span class="arrow">›</span>
      </div>
      <div class="menu-item">
        <span>关于我们</span>
        <span class="arrow">›</span>
      </div>
    </div>
  </div>
</template>

<script setup>
</script>

<style scoped>
.mine-page {
  padding: 1.5rem 1rem;
}

.avatar-section {
  text-align: center;
  padding: 2rem 0;
}

.avatar {
  width: 4rem;
  height: 4rem;
  border-radius: 50%;
  background: var(--bg-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 2rem;
  margin: 0 auto 0.75rem;
}

.username {
  font-size: 1rem;
  color: var(--text-primary);
  font-weight: 600;
}

.menu-list {
  background: white;
  border-radius: 0.75rem;
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}

.menu-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.875rem 1rem;
  font-size: 0.875rem;
  color: var(--text-primary);
  border-bottom: 1px solid var(--border-color);
  cursor: pointer;
}

.menu-item:last-child {
  border-bottom: none;
}

.menu-item:active {
  background: var(--bg-secondary);
}

.arrow {
  color: var(--text-hint);
  font-size: 1.1rem;
}
</style>
