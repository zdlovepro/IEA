<template>
  <footer class="footer">
    <div class="footer-container">
      <div class="footer-content">
        <p class="footer-text">© 2026 SoftDesign. All rights reserved.</p>
        <div class="footer-links">
          <a href="#">关于项目</a>
          <span class="divider">|</span>
          <a href="#">文档</a>
          <span class="divider">|</span>
          <a href="#">反馈</a>
        </div>
      </div>
    </div>
  </footer>
</template>

<script setup>
</script>

<style scoped>
.footer {
  flex-shrink: 0;
  background: var(--bg-secondary);
  border-top: 1px solid var(--border-color);
  z-index: 99;
}

.footer-container {
  max-width: 100%;
  margin: 0 auto;
  padding: 1rem;
}

.footer-content {
  text-align: center;
}

.footer-text {
  font-size: var(--font-size-xs);
  color: var(--text-hint);
  margin: 0 0 0.5rem 0;
}

.footer-links {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.footer-links a {
  font-size: var(--font-size-xs);
  color: var(--primary-color);
  text-decoration: none;
  transition: color var(--transition-fast);
}

.footer-links a:hover {
  color: var(--secondary-color);
}

.divider {
  color: var(--border-color);
}

/* 移动端适配 */
@media (max-width: 480px) {
  .footer-container {
    padding: 0.75rem 0.5rem;
  }

  .footer-text {
    font-size: var(--font-size-xs);
  }

  .footer-links a {
    font-size: var(--font-size-xs);
  }
}
</style>
