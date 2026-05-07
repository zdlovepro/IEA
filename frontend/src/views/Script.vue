<template>
  <div class="script-page">
    <div class="header-info">
      <h1>讲稿预览与编辑</h1>
      <div class="actions">
        <button
          class="btn btn-primary"
          :disabled="!scriptData || scriptStatus === 'GENERATING'"
          @click="startLecturePage"
        >
          开始讲课
        </button>
        <button
          v-if="!scriptData && scriptStatus !== 'GENERATING'"
          class="btn btn-secondary"
          @click="handleGenerateScript"
        >
          生成讲稿
        </button>
        <button class="btn btn-secondary" @click="goBack">返回</button>
      </div>
    </div>

    <div class="script-content">
      <div v-if="loading" class="loading">加载中...</div>

      <div v-else-if="scriptStatus === 'GENERATING'" class="loading">
        <div class="generating-hint">
          <span class="spinner"></span>
          <p>讲稿正在生成中，请稍候...</p>
        </div>
      </div>

      <div v-else-if="scriptData" class="script-body">
        <div class="script-outline">
          <h2>课程大纲</h2>
          <ul>
            <li
              v-for="(item, index) in scriptData.outline"
              :key="item.id"
              :class="{ active: activeSegmentId === item.id }"
              @click="scrollToSegment(item.id)"
            >
              <span class="outline-index">{{ index + 1 }}.</span>
              {{ item.title }}
            </li>
          </ul>
        </div>

        <div class="script-segments" ref="segmentsRef">
          <h2>讲稿片段</h2>
          <div
            v-for="segment in scriptData.segments"
            :key="segment.id"
            :id="'segment-' + segment.id"
            class="segment"
            :class="{ active: activeSegmentId === segment.id }"
          >
            <h3>{{ segment.title }}</h3>
            <p>{{ segment.content }}</p>
            <div class="segment-meta" v-if="segment.knowledgePoints?.length">
              <span class="knowledge-point" v-for="kp in segment.knowledgePoints" :key="kp">
                {{ kp }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <div v-else class="no-data">
        <p>暂无讲稿数据</p>
        <button class="btn btn-primary" @click="handleGenerateScript">生成讲稿</button>
      </div>
    </div>

    <div v-if="errorMsg" class="error-toast" @click="errorMsg = ''">
      鈿狅笍 {{ errorMsg }}
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { generateScript, getCoursewareScript } from '@/api/courseware'
import { getErrorMessage } from '@/utils'

const router = useRouter()
const route = useRoute()

const loading = ref(true)
const scriptData = ref(null)
const scriptStatus = ref(null)
const activeSegmentId = ref(null)
const errorMsg = ref('')
const segmentsRef = ref(null)

let pollTimer = null

const coursewareId = route.params.coursewareId

const showError = (error, fallback) => {
  errorMsg.value = getErrorMessage(error, fallback)
}

const fetchScript = async () => {
  loading.value = true
  try {
    const response = await getCoursewareScript(coursewareId)
    if (response.data) {
      scriptData.value = response.data
      scriptStatus.value = response.data.status || 'READY'
      if (response.data.outline?.length) {
        activeSegmentId.value = response.data.outline[0].id
      }
    } else {
      scriptData.value = null
      scriptStatus.value = null
    }
  } catch (error) {
    showError(error, '无法获取讲稿')
  } finally {
    loading.value = false
  }
}

const handleGenerateScript = async () => {
  scriptStatus.value = 'GENERATING'
  errorMsg.value = ''

  try {
    await generateScript(coursewareId)
    pollGenerateStatus()
  } catch (error) {
    scriptStatus.value = null
    showError(error, '生成讲稿失败')
  }
}

const pollGenerateStatus = () => {
  let attempts = 0
  const maxAttempts = 40

  if (pollTimer) {
    clearInterval(pollTimer)
  }

  pollTimer = setInterval(async () => {
    attempts += 1
    if (attempts > maxAttempts) {
      clearInterval(pollTimer)
      scriptStatus.value = null
      errorMsg.value = '讲稿生成超时，请重试'
      return
    }

    try {
      const response = await getCoursewareScript(coursewareId)
      if (response.data) {
        clearInterval(pollTimer)
        scriptData.value = response.data
        scriptStatus.value = response.data.status || 'READY'
        if (response.data.outline?.length) {
          activeSegmentId.value = response.data.outline[0].id
        }
      }
    } catch {
      // 轮询期间忽略瞬时网络错误
    }
  }, 3000)
}

const scrollToSegment = segmentId => {
  activeSegmentId.value = segmentId
  const element = document.getElementById(`segment-${segmentId}`)
  if (element) {
    element.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

const startLecturePage = () => {
  router.push({
    name: 'Lecture',
    params: { coursewareId },
  })
}

const goBack = () => {
  router.back()
}

onMounted(() => {
  fetchScript()
})

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
  }
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

.no-data .btn {
  margin-top: 1rem;
}

.generating-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  padding: 3rem;
}

.spinner {
  display: inline-block;
  width: 2rem;
  height: 2rem;
  border: 3px solid var(--border-color);
  border-top-color: var(--primary-color);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.script-outline li {
  padding: 0.5rem 0.75rem;
  border-bottom: 1px solid var(--border-color);
  cursor: pointer;
  border-radius: var(--radius-sm);
  transition: background 0.2s;
}

.script-outline li:hover {
  background: rgba(102, 126, 234, 0.08);
}

.script-outline li.active {
  background: rgba(102, 126, 234, 0.15);
  color: var(--primary-color);
  font-weight: 600;
}

.outline-index {
  color: var(--primary-color);
  margin-right: 0.25rem;
}

.segment.active {
  border-left-color: var(--secondary-color);
  background: rgba(102, 126, 234, 0.08);
}

.error-toast {
  position: fixed;
  bottom: 5rem;
  left: 50%;
  transform: translateX(-50%);
  padding: 0.75rem 1.5rem;
  background: var(--error-color);
  color: white;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
  cursor: pointer;
  z-index: 100;
}

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
