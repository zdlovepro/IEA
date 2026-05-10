<template>
  <div class="home-page">
    <section class="page-shell hero-section">
      <div class="hero-copy">
        <span class="eyebrow">AI 教学工作流</span>
        <h1>让 AI 成为你的互动课堂讲师</h1>
        <p>
          上传课件，自动生成讲稿、语音讲解，并支持学生实时打断提问。
        </p>
        <div class="hero-actions">
          <AppButton size="lg" @click="router.push('/upload')">开始上传课件</AppButton>
          <AppButton variant="secondary" size="lg" @click="router.push('/mine')">
            查看我的课程
          </AppButton>
        </div>
        <div class="hero-metrics">
          <div class="metric-item">
            <strong>课件解析</strong>
            <span>自动抽取页面结构与知识点</span>
          </div>
          <div class="metric-item">
            <strong>语音讲解</strong>
            <span>支持 TTS 音频与浏览器朗读兜底</span>
          </div>
          <div class="metric-item">
            <strong>互动问答</strong>
            <span>课堂内打断提问，基于课件内容回答</span>
          </div>
        </div>
      </div>

      <AppCard class="hero-panel" tone="accent">
        <div class="hero-panel__header">
          <span class="hero-panel__chip">课堂工作流</span>
          <span class="hero-panel__subtle">现代 AI 教学平台</span>
        </div>

        <div class="hero-steps">
          <div class="hero-step" v-for="step in heroSteps" :key="step.title">
            <div class="hero-step__index">{{ step.index }}</div>
            <div>
              <h3>{{ step.title }}</h3>
              <p>{{ step.description }}</p>
            </div>
          </div>
        </div>
      </AppCard>
    </section>

    <section class="page-shell page-section">
      <div class="section-header">
        <div>
          <span class="eyebrow">核心能力</span>
          <h2 class="page-title section-title">围绕教学交付设计，而不是只做一次内容生成</h2>
        </div>
      </div>

      <div class="feature-grid">
        <AppCard
          v-for="feature in featureCards"
          :key="feature.title"
          hoverable
          class="feature-card"
        >
          <span class="feature-kicker">{{ feature.kicker }}</span>
          <h3>{{ feature.title }}</h3>
          <p>{{ feature.description }}</p>
        </AppCard>
      </div>
    </section>

    <section class="page-shell page-section workflow-section">
      <AppCard tone="glass" class="workflow-card">
        <div class="section-header">
          <div>
            <span class="eyebrow">流程体验</span>
            <h2 class="page-title section-title">从课件到课堂，一条链路完成</h2>
            <p class="page-description">
              适合老师课前准备、课堂演示和教学复盘，不需要在多个工具之间来回切换。
            </p>
          </div>
        </div>

        <div class="workflow-grid">
          <div class="workflow-node" v-for="item in workflow" :key="item.title">
            <span class="workflow-node__index">{{ item.index }}</span>
            <h3>{{ item.title }}</h3>
            <p>{{ item.description }}</p>
          </div>
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

const heroSteps = [
  {
    index: '01',
    title: '上传课件',
    description: '支持 PDF、PPT 与 PPTX，上传后自动进入解析流程。',
  },
  {
    index: '02',
    title: '生成讲稿',
    description: '自动组织开场、逐页讲解、过渡与收尾，方便老师校阅。',
  },
  {
    index: '03',
    title: '开始互动课堂',
    description: '课堂讲解与 AI 助教并行运行，支持实时提问与证据展示。',
  },
]

const featureCards = [
  {
    kicker: '课件解析',
    title: '把静态课件变成结构化教学内容',
    description: '自动识别页面内容、组织分段，并为后续检索、讲稿与课堂互动打底。',
  },
  {
    kicker: '讲稿生成',
    title: '生成适合朗读的教学讲稿',
    description: '输出更自然的口语化讲稿结构，方便老师快速检查与课堂试讲。',
  },
  {
    kicker: '语音讲解',
    title: '让每一页都能直接进入讲解状态',
    description: '支持云端音频与本地朗读兜底，便于演示、录制和后续扩展。',
  },
  {
    kicker: '互动问答',
    title: '围绕当前课件内容回答问题',
    description: '优先参考当前页与相邻页内容，减少课堂打断后的定位成本。',
  },
]

const workflow = [
  {
    index: '一',
    title: '上传课件',
    description: '统一管理课程资源，快速发起一次新的教学准备。',
  },
  {
    index: '二',
    title: '自动解析',
    description: '抽取文字、页面结构与知识点，为后续生成提供上下文。',
  },
  {
    index: '三',
    title: '生成讲稿',
    description: '形成适合检查与朗读的结构化讲稿，可继续补充语音资源。',
  },
  {
    index: '四',
    title: '开始互动课堂',
    description: '进入教学工作台，控制讲解进度并接入 AI 助教问答。',
  },
]
</script>

<style scoped>
.home-page {
  padding-bottom: 2rem;
}

.hero-section {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(320px, 0.85fr);
  gap: 1.5rem;
  padding-top: 3.25rem;
  align-items: stretch;
}

.hero-copy {
  padding: 3.2rem;
  border: 1px solid rgba(126, 136, 255, 0.16);
  border-radius: calc(var(--radius-xl) + 0.25rem);
  background:
    radial-gradient(circle at top left, rgba(138, 123, 255, 0.28), transparent 34%),
    radial-gradient(circle at bottom right, rgba(72, 165, 255, 0.22), transparent 30%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.95), rgba(245, 248, 255, 0.94));
  box-shadow: var(--shadow-lg);
}

.hero-copy h1 {
  margin: 0;
  font-size: var(--font-size-3xl);
  line-height: 1;
  letter-spacing: -0.04em;
}

.hero-copy p {
  max-width: 42rem;
  margin: 1.2rem 0 0;
  color: var(--text-secondary);
  font-size: 1.08rem;
  line-height: 1.85;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.9rem;
  margin-top: 2rem;
}

.hero-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1rem;
  margin-top: 2.5rem;
}

.metric-item {
  padding: 1rem;
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.58);
  border: 1px solid rgba(131, 142, 181, 0.12);
}

.metric-item strong {
  display: block;
  font-size: 1rem;
  margin-bottom: 0.45rem;
}

.metric-item span {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.7;
}

.hero-panel {
  padding: 2rem;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.hero-panel__header {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: center;
}

.hero-panel__chip {
  display: inline-flex;
  align-items: center;
  min-height: 2rem;
  padding: 0.4rem 0.75rem;
  border-radius: 999px;
  background: rgba(95, 104, 255, 0.1);
  color: var(--primary-color);
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.hero-panel__subtle {
  color: var(--text-tertiary);
  font-size: var(--font-size-sm);
}

.hero-steps {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin-top: 1.8rem;
}

.hero-step {
  display: flex;
  gap: 1rem;
  padding: 1.1rem;
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(122, 132, 181, 0.12);
}

.hero-step__index {
  width: 2.6rem;
  height: 2.6rem;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  border-radius: 0.9rem;
  background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
  color: #ffffff;
  font-weight: 700;
}

.hero-step h3,
.feature-card h3,
.workflow-node h3 {
  margin: 0;
  font-size: 1.08rem;
}

.hero-step p,
.feature-card p,
.workflow-node p {
  margin: 0.55rem 0 0;
  color: var(--text-secondary);
  line-height: 1.75;
}

.section-title {
  font-size: clamp(2rem, 3vw, 2.7rem);
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1rem;
}

.feature-card {
  min-height: 14rem;
}

.feature-kicker {
  display: inline-flex;
  margin-bottom: 1rem;
  color: var(--secondary-color);
  font-size: var(--font-size-xs);
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.workflow-card {
  padding: 2rem;
}

.workflow-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1rem;
}

.workflow-node {
  padding: 1.2rem;
  border-radius: var(--radius-lg);
  background: rgba(248, 250, 255, 0.78);
  border: 1px solid rgba(128, 139, 182, 0.12);
}

.workflow-node__index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.4rem;
  height: 2.4rem;
  margin-bottom: 0.9rem;
  border-radius: 0.85rem;
  background: rgba(95, 104, 255, 0.1);
  color: var(--primary-color);
  font-weight: 700;
}

@media (max-width: 1080px) {
  .hero-section,
  .feature-grid,
  .workflow-grid {
    grid-template-columns: 1fr;
  }

  .hero-metrics {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .hero-section {
    padding-top: 2rem;
  }

  .hero-copy,
  .hero-panel,
  .workflow-card {
    padding: 1.35rem;
  }
}
</style>
