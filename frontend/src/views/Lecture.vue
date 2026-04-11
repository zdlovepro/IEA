<template>
  <div class="lecture-page">
    <div class="lecture-content">
      <div class="slide-section">
        <h2>{{ currentSlide?.title || '讲课中...' }}</h2>
        <div class="slide-body">
          {{ currentSlide?.content || '加载中...' }}
        </div>
      </div>

      <div class="controls">
        <button class="btn btn-control" @click="previousSlide">⬅️ 上一页</button>
        <span class="progress">{{ currentPage }}/{{ totalPages }}</span>
        <button class="btn btn-control" @click="nextSlide">下一页 ➡️</button>
      </div>
    </div>

    <div class="qa-section">
      <h3>学生问答</h3>
      <div class="qa-input">
        <input
          v-model="question"
          type="text"
          placeholder="输入您的问题..."
          @keyup.enter="submitQuestion"
        />
        <button class="btn btn-small" @click="submitQuestion" :disabled="!question">
          提问
        </button>
      </div>

      <div class="qa-history">
        <div v-for="qa in qaHistory" :key="qa.id" class="qa-item">
          <div class="qa-question">Q: {{ qa.question }}</div>
          <div class="qa-answer">A: {{ qa.answer }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed onMounted } from 'vue'
import { useCoursStore } from '@/stores/cours'
import request from '@/utils/request'

const coursStore = useCoursStore()
const question = ref('')

// 假数据 - 实际从后端获取
const slides = ref([
  { id: 1, title: '课程介绍', content: '这是一个示例课程...' },
  { id: 2, title: '第一章', content: '第一章的内容...' },
])

const currentPage = ref(1)

const currentSlide = computed(() => slides.value[currentPage.value - 1])
const totalPages = computed(() => slides.value.length)
const qaHistory = computed(() => coursStore.qaHistory)

const previousSlide = () => {
  if (currentPage.value > 1) currentPage.value--
}

const nextSlide = () => {
  if (currentPage.value < totalPages.value) currentPage.value++
}

const submitQuestion = async () => {
  if (!question.value.trim()) return

  try {
    // 将问题添加到本地历史
    coursStore.addQARecord({
      question: question.value,
      answer: '正在获取答案...',
    })

    // 实际调用后端 API
    // const response = await request.post('/api/v1/qa/ask-text', {
    //   sessionId: coursStore.currentSession?.id,
    //   question: question.value,
    // })

    // 这里是模拟回答
    setTimeout(() => {
      const lastQA = qaHistory.value[qaHistory.value.length - 1]
      if (lastQA) {
        lastQA.answer = '这是对您问题的回答。（示例数据）'
      }
    }, 500)

    question.value = ''
  } catch (error) {
    console.error('提问失败:', error)
    alert('提问失败，请重试')
  }
}

onMounted(() => {
  // 如果没有当前会话，返回首页
  if (!coursStore.isLectureActive) {
    // 跳转回首页或上传页
  }
})
</script>

<style scoped>
.lecture-page {
  display: flex;
  gap: 1.5rem;
  padding: 1.5rem;
  height: calc(100vh - 9rem); /* Header + Footer */
  overflow: hidden;
}

.lecture-content {
  flex: 2;
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  overflow: hidden;
}

.slide-section {
  flex: 1;
  padding: 2rem;
  overflow-y: auto;
}

.slide-section h2 {
  font-size: var(--font-size-xl);
  margin-bottom: 1rem;
  color: var(--primary-color);
}

.slide-body {
  font-size: var(--font-size-md);
  line-height: var(--line-height-relaxed);
  color: var(--text-secondary);
  min-height: 10rem;
}

.controls {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 2rem;
  border-top: 1px solid var(--border-color);
  background: var(--bg-secondary);
}

.progress {
  font-weight: 600;
  color: var(--text-primary);
}

.qa-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  padding: 1.5rem;
  overflow: hidden;
}

.qa-section h3 {
  margin: 0 0 1rem 0;
  color: var(--primary-color);
}

.qa-input {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1rem;
}

.qa-input input {
  flex: 1;
  padding: 0.5rem 1rem;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
}

.qa-history {
  flex: 1;
  overflow-y: auto;
}

.qa-item {
  margin-bottom: 1rem;
  padding: 0.75rem;
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
}

.qa-question {
  font-weight: 600;
  color: var(--primary-color);
  margin-bottom: 0.5rem;
  font-size: var(--font-size-sm);
}

.qa-answer {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

/* 移动端适配 */
@media (max-width: 768px) {
  .lecture-page {
    flex-direction: column;
    gap: 1rem;
    padding: 1rem;
    height: auto;
  }

  .lecture-content,
  .qa-section {
    flex: none;
    height: 50%;
  }

  .slide-section {
    padding: 1rem;
  }

  .slide-section h2 {
    font-size: var(--font-size-lg);
  }

  .controls {
    padding: 0.75rem 1rem;
  }
}

@media (max-width: 480px) {
  .lecture-page {
    padding: 0.5rem;
    gap: 0.5rem;
  }

  .slide-body {
    min-height: 8rem;
    font-size: var(--font-size-sm);
  }

  .qa-section {
    padding: 1rem;
  }

  .btn {
    padding: 0.5rem 1rem;
    font-size: var(--font-size-sm);
  }
}
</style>
