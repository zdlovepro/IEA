<template>
  <div class="script-page">
    <div class="header-info">
      <h1>讲稿预览与编辑</h1>
      <div class="actions">
        <button class="btn btn-primary" @click="startLecture">开始讲课</button>
        <button class="btn btn-secondary" @click="goBack">返回</button>
      </div>
    </div>

    <div class="script-content">
      <div v-if="loading" class="loading">加载中...</div>
      <div v-else-if="scriptData" class="script-body">
        <div class="script-outline">
          <h2>课程大纲</h2>
          <ul>
            <li v-for="item in scriptData.outline" :key="item.id">
              {{ item.title }}
            </li>
          </ul>
        </div>

        <div class="script-segments">
          <h2>讲稿片段</h2>
          <div v-for="segment in scriptData.segments" :key="segment.id" class="segment">
            <h3>{{ segment.title }}</h3>
            <p>{{ segment.content }}</p>
            <div class="segment-meta">
              <span class="knowledge-point" v-for="kp in segment.knowledgePoints" :key="kp">
                {{ kp }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <div v-else class="no-data">暂无讲稿数据，请先上传课件。</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useCoursStore } from '@/stores/cours'

const router = useRouter()
const route = useRoute()
const coursStore = useCoursStore()

const loading = ref(true)
const scriptData = ref(null)

const mockScript = {
  outline: [
    { id: 1, title: '课程介绍' },
    { id: 2, title: '第一章：基础概念' },
    { id: 3, title: '第二章：实践应用' },
    { id: 4, title: '总结与回顾' },
  ],
  segments: [
    {
      id: 1,
      title: '课程介绍',
      content:
        '欢迎来到本课程。本课程将系统地介绍相关知识点，帮助学生建立清晰的知识框架。',
      knowledgePoints: ['课程目标', '学习路径'],
    },
    {
      id: 2,
      title: '基础概念',
      content: '首先，我们来了解一下基础概念的定义和意义。这是理解后续内容的基础。',
      knowledgePoints: ['定义', '分类', '应用'],
    },
  ],
}

const startLecture = () => {
  coursStore.createSession({
    coursewareId: route.params.coursewareId,
    type: 'lecture',
  })
  router.push({
    name: 'Lecture',
    params: { coursewareId: route.params.coursewareId },
  })
}

const goBack = () => {
  router.back()
}

onMounted(() => {
  // 模拟加载数据
  setTimeout(() => {
    scriptData.value = mockScript
    loading.value = false
  }, 500)
})
</script>

<style scoped>
.script-page {
  padding: 2rem 1rem;
  max-width: 1000px;
  margin: 0 auto;
}

.header-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2rem;
}

.header-info h1 {
  font-size: var(--font-size-2xl);
  color: var(--primary-color);
  margin: 0;
}

.actions {
  display: flex;
  gap: 1rem;
}

.script-content {
  background: white;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  padding: 2rem;
}

.loading {
  text-align: center;
  padding: 3rem;
  color: var(--text-secondary);
}

.script-body {
  max-height: 70vh;
  overflow-y: auto;
}

.script-outline {
  margin-bottom: 3rem;
}

.script-outline h2 {
  font-size: var(--font-size-lg);
  color: var(--primary-color);
  margin-bottom: 1rem;
}

.script-outline ul {
  list-style: none;
  padding: 1rem;
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
}

.script-outline li {
  padding: 0.5rem 0;
  border-bottom: 1px solid var(--border-color);
}

.script-outline li:last-child {
  border-bottom: none;
}

.script-segments h2 {
  font-size: var(--font-size-lg);
  color: var(--primary-color);
  margin-bottom: 1rem;
}

.segment {
  padding: 1.5rem;
  margin-bottom: 1rem;
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
  border-left: 4px solid var(--primary-color);
}

.segment h3 {
  font-size: var(--font-size-md);
  margin: 0 0 0.5rem 0;
  color: var(--text-primary);
}

.segment p {
  margin: 0.5rem 0;
  color: var(--text-secondary);
  line-height: var(--line-height-relaxed);
}

.segment-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 1rem;
}

.knowledge-point {
  display: inline-block;
  padding: 0.25rem 0.75rem;
  background: rgba(102, 126, 234, 0.1);
  color: var(--primary-color);
  border-radius: var(--radius-sm);
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.no-data {
  text-align: center;
  padding: 2rem;
  color: var(--text-secondary);
}

/* 移动端适配 */
@media (max-width: 768px) {
  .script-page {
    padding: 1.5rem 1rem;
  }

  .header-info {
    flex-direction: column;
    align-items: flex-start;
    gap: 1rem;
  }

  .header-info h1 {
    font-size: var(--font-size-xl);
  }

  .actions {
    width: 100%;
  }

  .actions .btn {
    flex: 1;
  }

  .script-content {
    padding: 1.5rem;
  }

  .script-body {
    max-height: 60vh;
  }
}

@media (max-width: 480px) {
  .script-page {
    padding: 1rem 0.75rem;
  }

  .header-info h1 {
    font-size: var(--font-size-lg);
  }

  .script-content {
    padding: 1rem;
  }

  .segment {
    padding: 1rem;
  }
}
</style>
