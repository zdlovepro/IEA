<template>
  <div class="lecture-page">
    <div class="lecture-content">
      <div class="slide-section">
        <div class="slide-header">
          <h2>{{ currentSlide?.title || '加载中...' }}</h2>
          <span class="state-badge" :class="statusMeta.className">{{ statusMeta.text }}</span>
        </div>
        <div class="slide-body">
          {{ currentSlide?.content || '' }}
        </div>
        <div class="knowledge-tags" v-if="currentSlide?.knowledgePoints?.length">
          <span class="tag" v-for="kp in currentSlide.knowledgePoints" :key="kp">{{ kp }}</span>
        </div>
      </div>

      <div class="controls">
        <button class="btn btn-control" @click="previousSlide" :disabled="currentPage <= 1">
          猬咃笍 上一页
        </button>

        <div class="center-controls">
          <button class="btn btn-tts" @click="toggleSpeech" :class="{ speaking: isSpeaking }">
            {{ isSpeaking ? '馃攰 朗读中' : '馃攬 朗读' }}
          </button>

          <button
            v-if="lectureStatus === LECTURE_STATE.PLAYING"
            class="btn btn-pause"
            @click="handlePauseLecture"
            :disabled="lectureStore.isLoading || !lectureStore.sessionId"
          >
            鈴革笍 暂停
          </button>
          <button
            v-else-if="lectureStatus === LECTURE_STATE.INTERRUPTED"
            class="btn btn-resume"
            @click="handleResumeLecture"
            :disabled="lectureStore.isLoading || !lectureStore.sessionId"
          >
            鈻讹笍 继续
          </button>

          <span class="progress">{{ currentPage }} / {{ totalPages }}</span>
        </div>

        <button class="btn btn-control" @click="nextSlide" :disabled="currentPage >= totalPages">
          下一页 鉃★笍
        </button>
      </div>

      <div class="progress-bar">
        <div class="progress-fill" :style="{ width: progressPercent + '%' }"></div>
      </div>
    </div>

    <div class="qa-section">
      <h3>馃挰 学生问答</h3>
      <div class="qa-input">
        <input
          v-model="question"
          type="text"
          placeholder="输入您的问题..."
          :disabled="isAsking"
          @keyup.enter="submitQuestion"
        />
        <button class="btn btn-small" @click="submitQuestion" :disabled="!question.trim() || isAsking">
          {{ isAsking ? '...' : '提问' }}
        </button>
      </div>

      <div class="qa-history" ref="qaHistoryRef">
        <div v-for="qa in qaList" :key="qa.id" class="qa-item">
          <div class="qa-question">Q: {{ qa.question }}</div>
          <div class="qa-answer" v-html="renderMd(qa.answer)"></div>
          <div class="qa-evidence" v-if="qa.evidence?.length">
            <details>
              <summary>馃搸 参考来源 ({{ qa.evidence.length }})</summary>
              <div v-for="(ev, i) in qa.evidence" :key="i" class="evidence-item">
                <span class="evidence-source">{{ ev.source }}</span>
                {{ ev.text }}
              </div>
            </details>
          </div>
        </div>
        <div v-if="qaList.length === 0" class="qa-empty">暂无问答记录，试试问一个问题吧</div>
      </div>
    </div>

    <div v-if="errorMsg" class="error-toast" @click="clearError">鈿狅笍 {{ errorMsg }}</div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { marked } from 'marked'
import { getCoursewareScript } from '@/api/courseware'
import { pauseLecture, resumeLecture, startLecture } from '@/api/lecture'
import { askText } from '@/api/qa'
import { LECTURE_STATE, LECTURE_STATUS_MAP, normalizeLectureStatus } from '@/constants/lecture'
import { useLectureStore } from '@/stores/lecture'
import { getErrorMessage } from '@/utils'

const route = useRoute()
const lectureStore = useLectureStore()

const coursewareId = route.params.coursewareId

const slides = ref([])
const question = ref('')
const isAsking = ref(false)
const qaList = ref([])
const qaHistoryRef = ref(null)
const isSpeaking = ref(false)

let speechUtterance = null

const lectureStatus = computed(() => normalizeLectureStatus(lectureStore.status))
const statusMeta = computed(() => LECTURE_STATUS_MAP[lectureStatus.value] || LECTURE_STATUS_MAP[LECTURE_STATE.IDLE])
const currentPage = computed(() => lectureStore.currentPage)
const totalPages = computed(() => slides.value.length)
const currentSlide = computed(() => slides.value[currentPage.value - 1] || null)
const progressPercent = computed(() => {
  if (!totalPages.value) {
    return 0
  }
  return Math.round((currentPage.value / totalPages.value) * 100)
})
const errorMsg = computed(() => lectureStore.errorMessage)

const renderMd = text => {
  if (!text) return ''
  return marked.parse(text, { breaks: true })
}

const clearError = () => {
  lectureStore.setErrorMessage('')
}

const showError = (error, fallback) => {
  lectureStore.setErrorMessage(getErrorMessage(error, fallback))
}

const syncCurrentNodeWithSlide = page => {
  const slide = slides.value[page - 1]
  if (!slide) {
    return
  }

  lectureStore.setCurrentNode({
    nodeId: slide.nodeId || slide.id,
    pageIndex: slide.pageIndex || page,
    content: slide.content,
    audioUrl: slide.audioUrl || null,
  })
}

const loadSlides = async () => {
  lectureStore.setLoading(true)
  clearError()

  try {
    const response = await getCoursewareScript(coursewareId)
    const segments = response.data?.segments || []

    if (!segments.length) {
      slides.value = []
      showError(null, '讲稿数据为空，请先生成讲稿')
      return false
    }

    slides.value = segments
    lectureStore.setCurrentPage(1)
    syncCurrentNodeWithSlide(1)
    return true
  } catch (error) {
    slides.value = []
    showError(error, '无法加载讲稿数据')
    return false
  } finally {
    lectureStore.setLoading(false)
  }
}

const startLectureSession = async () => {
  lectureStore.setLoading(true)
  clearError()

  try {
    lectureStore.setCoursewareId(coursewareId)
    const response = await startLecture({ coursewareId })
    lectureStore.syncFromStartResponse({
      ...response.data,
      coursewareId,
    })
    if (!response.data?.currentNode?.pageIndex) {
      syncCurrentNodeWithSlide(currentPage.value)
    }
  } catch (error) {
    showError(error, '无法开始讲课')
  } finally {
    lectureStore.setLoading(false)
  }
}

const handlePauseLecture = async () => {
  if (!lectureStore.sessionId) {
    return
  }

  lectureStore.setLoading(true)
  clearError()
  stopSpeech()

  try {
    const response = await pauseLecture(lectureStore.sessionId)
    lectureStore.setStatus(response.data?.status)
  } catch (error) {
    showError(error, '暂停讲课失败')
  } finally {
    lectureStore.setLoading(false)
  }
}

const handleResumeLecture = async () => {
  if (!lectureStore.sessionId) {
    return
  }

  lectureStore.setLoading(true)
  clearError()

  try {
    const response = await resumeLecture({ sessionId: lectureStore.sessionId })
    lectureStore.syncFromStartResponse({
      ...response.data,
      coursewareId,
    })
    if (!response.data?.currentNode?.pageIndex) {
      syncCurrentNodeWithSlide(currentPage.value)
    }
  } catch (error) {
    showError(error, '恢复讲课失败')
  } finally {
    lectureStore.setLoading(false)
  }
}

const previousSlide = () => {
  if (currentPage.value <= 1) {
    return
  }

  lectureStore.setCurrentPage(currentPage.value - 1)
  syncCurrentNodeWithSlide(currentPage.value)
  stopSpeech()
}

const nextSlide = () => {
  if (currentPage.value >= totalPages.value) {
    stopSpeech()
    return
  }

  lectureStore.setCurrentPage(currentPage.value + 1)
  syncCurrentNodeWithSlide(currentPage.value)
  stopSpeech()
}

const toggleSpeech = () => {
  if (isSpeaking.value) {
    stopSpeech()
    return
  }

  speakCurrent()
}

const speakCurrent = () => {
  const text = currentSlide.value?.content
  if (!text || !globalThis.speechSynthesis) {
    return
  }

  stopSpeech()
  speechUtterance = new SpeechSynthesisUtterance(text)
  speechUtterance.lang = 'zh-CN'
  speechUtterance.rate = 1
  speechUtterance.onend = () => {
    isSpeaking.value = false
  }
  speechUtterance.onerror = () => {
    isSpeaking.value = false
  }

  globalThis.speechSynthesis.speak(speechUtterance)
  isSpeaking.value = true
}

const stopSpeech = () => {
  if (globalThis.speechSynthesis) {
    globalThis.speechSynthesis.cancel()
  }
  isSpeaking.value = false
}

const submitQuestion = async () => {
  const normalizedQuestion = question.value.trim()
  if (!normalizedQuestion || isAsking.value || !lectureStore.sessionId) {
    return
  }

  isAsking.value = true
  clearError()
  const qaItem = {
    id: Date.now(),
    question: normalizedQuestion,
    answer: '正在思考...',
    evidence: [],
  }

  qaList.value.push(qaItem)
  question.value = ''
  scrollQAToBottom()

  try {
    const response = await askText({
      sessionId: lectureStore.sessionId,
      question: normalizedQuestion,
    })
    qaItem.answer = response.data?.answer || '暂无答案'
    qaItem.evidence = response.data?.evidence || []
  } catch (error) {
    const message = getErrorMessage(error, '提问失败，请稍后重试')
    qaItem.answer = message
    showError(error, '提问失败，请稍后重试')
  } finally {
    isAsking.value = false
    scrollQAToBottom()
  }
}

const scrollQAToBottom = () => {
  nextTick(() => {
    if (qaHistoryRef.value) {
      qaHistoryRef.value.scrollTop = qaHistoryRef.value.scrollHeight
    }
  })
}

onMounted(async () => {
  lectureStore.reset()
  lectureStore.setCoursewareId(coursewareId)
  const loaded = await loadSlides()
  if (loaded) {
    await startLectureSession()
  }
})

onUnmounted(() => {
  stopSpeech()
  lectureStore.reset()
})
</script>

<style scoped>
.lecture-page {
  display: flex;
  gap: 1.5rem;
  padding: 1.5rem;
  height: calc(100vh - 9rem);
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

.slide-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.slide-header h2 {
  font-size: var(--font-size-xl);
  color: var(--primary-color);
  margin: 0;
}

.state-badge {
  font-size: var(--font-size-xs);
  padding: 0.25rem 0.75rem;
  border-radius: var(--radius-sm);
  font-weight: 600;
}
.state-badge.state-playing { background: rgba(82, 196, 26, 0.15); color: #389e0d; }
.state-badge.state-interrupted,
.state-badge.state-answering { background: rgba(250, 173, 20, 0.15); color: #d48806; }
.state-badge.state-resuming { background: rgba(102, 126, 234, 0.15); color: var(--primary-color); }
.state-badge.state-ended { background: rgba(0, 0, 0, 0.06); color: var(--text-secondary); }
.state-badge.state-idle { background: var(--bg-secondary); color: var(--text-secondary); }

.slide-body {
  font-size: var(--font-size-md);
  line-height: var(--line-height-relaxed);
  color: var(--text-secondary);
  min-height: 10rem;
}

.knowledge-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 1.5rem;
}

.knowledge-tags .tag {
  padding: 0.2rem 0.6rem;
  background: rgba(102, 126, 234, 0.1);
  color: var(--primary-color);
  border-radius: var(--radius-sm);
  font-size: var(--font-size-xs);
}

.controls {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 2rem;
  border-top: 1px solid var(--border-color);
  background: var(--bg-secondary);
  gap: 0.5rem;
}

.center-controls {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.btn-tts {
  font-size: var(--font-size-sm);
  padding: 0.4rem 0.8rem;
  border-radius: var(--radius-md);
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  cursor: pointer;
  transition: all 0.2s;
}
.btn-tts.speaking {
  background: rgba(82, 196, 26, 0.15);
  border-color: #52c41a;
  color: #389e0d;
}

.btn-pause, .btn-resume {
  font-size: var(--font-size-sm);
  padding: 0.4rem 0.8rem;
  border-radius: var(--radius-md);
  cursor: pointer;
  border: 1px solid var(--border-color);
  background: var(--bg-secondary);
  transition: all 0.2s;
}

.progress {
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
}

.progress-bar {
  height: 3px;
  background: var(--border-color);
}
.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--primary-color), var(--secondary-color));
  transition: width 0.3s ease;
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
  outline: none;
  transition: border-color 0.2s;
}
.qa-input input:focus {
  border-color: var(--primary-color);
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
  line-height: 1.6;
}
.qa-answer :deep(p) { margin: 0.25rem 0; }
.qa-answer :deep(code) {
  background: rgba(0,0,0,0.06);
  padding: 0.1rem 0.3rem;
  border-radius: 3px;
  font-size: 0.85em;
}
.qa-answer :deep(pre) {
  background: #f6f8fa;
  padding: 0.75rem;
  border-radius: var(--radius-sm);
  overflow-x: auto;
  font-size: 0.85em;
}

.qa-evidence {
  margin-top: 0.5rem;
  font-size: var(--font-size-xs);
}
.qa-evidence summary {
  cursor: pointer;
  color: var(--text-secondary);
}
.evidence-item {
  margin-top: 0.25rem;
  padding: 0.4rem;
  background: white;
  border-radius: var(--radius-sm);
  border-left: 3px solid var(--primary-color);
}
.evidence-source {
  font-weight: 600;
  color: var(--primary-color);
  margin-right: 0.5rem;
}

.qa-empty {
  text-align: center;
  color: var(--text-secondary);
  padding: 2rem 0;
  font-size: var(--font-size-sm);
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
  .lecture-page {
    flex-direction: column;
    gap: 1rem;
    padding: 1rem;
    height: auto;
    overflow-y: auto;
  }

  .lecture-content { flex: none; min-height: 50vh; }
  .qa-section { flex: none; min-height: 40vh; }

  .slide-section { padding: 1rem; }
  .slide-header h2 { font-size: var(--font-size-lg); }
  .controls { padding: 0.75rem 1rem; flex-wrap: wrap; }
  .center-controls { order: -1; width: 100%; justify-content: center; margin-bottom: 0.5rem; }
}

@media (max-width: 480px) {
  .lecture-page { padding: 0.5rem; gap: 0.5rem; }
  .slide-body { min-height: 8rem; font-size: var(--font-size-sm); }
  .qa-section { padding: 1rem; }
  .btn { padding: 0.5rem 1rem; font-size: var(--font-size-sm); }
}
</style>
