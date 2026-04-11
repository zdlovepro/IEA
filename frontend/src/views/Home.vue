<template>
  <div class="home">
    <div class="hero">
      <h1>AI 互动智课</h1>
      <p>基于泛雅平台的智课生成与实时问答系统</p>
      <p class="subtitle">上传课件，自动生成讲稿，支持实时语音问答</p>
    </div>

    <div class="quick-actions">
      <div class="action-card" @click="$router.push('/upload')">
        <div class="action-icon">📤</div>
        <div class="action-title">上传课件</div>
        <div class="action-desc">支持 PPT / PDF 格式，自动解析生成讲稿</div>
      </div>

      <div class="action-card" @click="$router.push('/courses')">
        <div class="action-icon">📚</div>
        <div class="action-title">我的课程</div>
        <div class="action-desc">查看已上传的课件与生成的讲稿</div>
      </div>

      <div class="action-card" @click="$router.push('/upload')">
        <div class="action-icon">🎓</div>
        <div class="action-title">开始学习</div>
        <div class="action-desc">AI 讲课播放，支持随时打断提问</div>
      </div>

      <div class="action-card">
        <div class="action-icon">💬</div>
        <div class="action-title">智能问答</div>
        <div class="action-desc">文字或语音提问，基于课件内容精准回答</div>
      </div>
    </div>

    <div class="features-section">
      <h2>核心功能</h2>
      <div class="feature-list">
        <div class="feature-row">
          <span class="feature-dot"></span>
          <span>课件智能解析（PPT/PDF → 知识点提取）</span>
        </div>
        <div class="feature-row">
          <span class="feature-dot"></span>
          <span>AI 自动生成结构化讲稿（开场/讲解/过渡/总结）</span>
        </div>
        <div class="feature-row">
          <span class="feature-dot"></span>
          <span>实时问答与语音交互（RAG 检索增强生成）</span>
        </div>
        <div class="feature-row">
          <span class="feature-dot"></span>
          <span>进度续接与节奏自适应（理解度感知）</span>
        </div>
        <div class="feature-row">
          <span class="feature-dot"></span>
          <span>学习进度可视化与教学效果看板</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
</script>

<style scoped>
.home {
  padding: 1.5rem 1rem;
  min-height: 100%;
}

.hero {
  text-align: center;
  padding: 2rem 0;
}

.hero h1 {
  font-size: 1.5rem;
  color: var(--primary-color);
  font-weight: 700;
  margin-bottom: 0.5rem;
}

.hero > p {
  font-size: 0.95rem;
  color: var(--text-secondary);
  margin: 0.25rem 0;
}

.hero .subtitle {
  font-size: 0.85rem;
  color: var(--text-hint);
}

/* 快捷操作卡片 */
.quick-actions {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0.75rem;
  margin-bottom: 2rem;
}

.action-card {
  background: white;
  padding: 1.25rem 1rem;
  border-radius: 0.75rem;
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.action-card:active {
  transform: scale(0.97);
  box-shadow: var(--shadow-md);
}

.action-icon {
  font-size: 1.75rem;
  margin-bottom: 0.5rem;
}

.action-title {
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 0.25rem;
}

.action-desc {
  font-size: 0.75rem;
  color: var(--text-hint);
  line-height: 1.4;
}

/* 核心功能列表 */
.features-section {
  background: white;
  border-radius: 0.75rem;
  padding: 1.25rem;
  box-shadow: var(--shadow-sm);
}

.features-section h2 {
  font-size: 1rem;
  color: var(--primary-color);
  margin-bottom: 0.75rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid var(--border-color);
}

.feature-list {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.feature-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
  color: var(--text-secondary);
  line-height: 1.4;
}

.feature-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--primary-color);
  flex-shrink: 0;
}

/* 移动端 */
@media (max-width: 360px) {
  .quick-actions {
    grid-template-columns: 1fr;
  }
}
</style>
