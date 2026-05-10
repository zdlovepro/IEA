<template>
  <div class="lecture-page">
    <section class="page-shell page-shell--wide page-section">
      <div class="lecture-layout">
        <div class="lecture-main">
          <div class="lecture-topbar">
            <div>
              <span class="eyebrow">AI 互动课堂</span>
              <h1>{{ currentSlide?.title || '正在准备课堂内容' }}</h1>
              <p class="lecture-subtitle">
                当前页 {{ currentPage }} / {{ totalPages }} · {{ playbackModeLabel }}
              </p>
            </div>

            <div class="lecture-topbar__meta">
              <StatusBadge :label="statusMeta.text" :tone="statusTone" />
              <span class="lecture-page-pill">第 {{ currentPage }} 页</span>
            </div>
          </div>

          <AppCard class="lecture-script-card" tone="glass">
            <template v-if="currentSlide">
              <div class="script-card__header">
                <div class="script-card__label-group">
                  <span class="pill">课堂讲稿</span>
                  <span class="audio-tag" :class="{ ready: Boolean(currentSlide.audioUrl) }">
                    {{ currentSlide.audioUrl ? '优先使用后端音频资源' : '当前页使用文本朗读兜底' }}
                  </span>
                </div>
                <div class="page-progress">
                  <span>{{ progressPercent }}%</span>
                </div>
              </div>

              <div class="script-card__content">
                <p>{{ currentSlide.content }}</p>
              </div>

              <div v-if="currentSlide.knowledgePoints?.length" class="knowledge-list">
                <span v-for="point in currentSlide.knowledgePoints" :key="point" class="knowledge-tag">
                  {{ point }}
                </span>
              </div>
            </template>

            <EmptyState
              v-else
              title="讲稿暂未加载完成"
              description="请稍候或先检查课件是否已经生成讲稿。"
            />
          </AppCard>

          <AppCard class="lecture-control-card" tone="subtle">
            <div class="control-grid">
              <div class="control-group">
                <span class="control-label">页码切换</span>
                <div class="control-row">
                  <AppButton variant="secondary" @click="previousSlide" :disabled="currentPage <= 1">
                    上一页
                  </AppButton>
                  <AppButton
                    variant="secondary"
                    @click="nextSlide"
                    :disabled="currentPage >= totalPages"
                  >
                    下一页
                  </AppButton>
                </div>
              </div>

              <div class="control-group">
                <span class="control-label">讲解控制</span>
                <div class="control-row">
                  <AppButton @click="togglePlayback" :disabled="!currentSlide">
                    {{ isSpeaking ? '停止朗读' : '开始朗读' }}
                  </AppButton>
                  <AppButton
                    v-if="lectureStatus === LECTURE_STATE.PLAYING"
                    variant="secondary"
                    :disabled="lectureStore.isLoading || !lectureStore.sessionId"
                    @click="handlePauseLecture"
                  >
                    暂停课堂
                  </AppButton>
                  <AppButton
                    v-else-if="lectureStatus === LECTURE_STATE.INTERRUPTED"
                    variant="secondary"
                    :disabled="lectureStore.isLoading || !lectureStore.sessionId"
                    @click="handleResumeLecture"
                  >
                    继续课堂
                  </AppButton>
                  <AppButton
                    v-else
                    variant="secondary"
                    :disabled="true"
                  >
                    {{ statusMeta.text }}
                  </AppButton>
                </div>
              </div>
            </div>

            <div class="timeline">
              <div class="audio-progress-header">
                <div>
                  <span class="control-label">播放进度</span>
                  <strong>{{ playbackModeLabel }}</strong>
                </div>
                <div class="audio-time">
                  <span>{{ formattedCurrentTime }}</span>
                  <span>/</span>
                  <span>{{ formattedDuration }}</span>
                </div>
              </div>

              <button
                type="button"
                class="audio-progress-track"
                :class="{ disabled: !canSeek }"
                :disabled="!canSeek"
                @click="handleSeek"
              >
                <span class="audio-progress-fill" :style="{ width: `${audioProgressPercent}%` }"></span>
              </button>

              <div class="timeline-meta">
                <span>页进度 {{ currentPage }} / {{ totalPages || 0 }}</span>
                <span>{{ currentSlide?.audioUrl ? '音频播放' : '文本朗读' }}</span>
              </div>
            </div>
          </AppCard>
        </div>

        <AppCard class="lecture-chat-panel" tone="glass">
          <div class="chat-header">
            <div>
              <span class="eyebrow">AI 助教问答</span>
              <h2>你可以针对当前课件内容提问</h2>
            </div>
            <span class="chat-header__status">{{ isAsking ? '思考中' : '等待提问' }}</span>
          </div>

          <div class="chat-history" ref="qaHistoryRef">
            <div v-if="qaList.length === 0" class="chat-empty">
              <h3>暂无问答记录，试着问一个问题吧。</h3>
              <p>系统会优先结合当前页与相邻页内容进行回答，并展示参考 evidence。</p>
            </div>

            <div v-for="qa in qaList" :key="qa.id" class="chat-turn">
              <div class="bubble bubble--user">
                <span class="bubble-role">学生</span>
                <p>{{ qa.question }}</p>
              </div>

              <div class="bubble bubble--assistant">
                <span class="bubble-role">AI 助教</span>
                <div class="bubble-answer" v-html="renderMd(qa.answer)"></div>

                <details v-if="qa.evidence?.length" class="evidence-panel">
                  <summary>查看 evidence（{{ qa.evidence.length }}）</summary>
                  <div class="evidence-list">
                    <div v-for="(evidence, index) in qa.evidence" :key="`${qa.id}-${index}`" class="evidence-item">
                      <div class="evidence-item__meta">
                        <span>{{ evidence.source || 'courseware' }}</span>
                        <span v-if="evidence.pageIndex">第 {{ evidence.pageIndex }} 页</span>
                      </div>
                      <p>{{ evidence.text }}</p>
                    </div>
                  </div>
                </details>
              </div>
            </div>
          </div>

          <div class="chat-composer">
            <label for="question-input" class="visually-hidden">输入问题</label>
            <input
              id="question-input"
              v-model="question"
              class="app-input"
              type="text"
              :disabled="isAsking"
              placeholder="输入你的问题，按 Enter 发送"
              @keyup.enter="submitQuestion"
            />
            <AppButton :disabled="!question.trim() || isAsking" @click="submitQuestion">
              {{ isAsking ? '思考中...' : '发送问题' }}
            </AppButton>
          </div>
        </AppCard>
      </div>
    </section>

    <div v-if="errorMsg" class="toast">
      <span>{{ errorMsg }}</span>
      <button @click="clearError">关闭</button>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { marked } from 'marked'
import AppButton from '@/components/ui/AppButton.vue'
import AppCard from '@/components/ui/AppCard.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { getCoursewareScript } from '@/api/courseware'
import { pauseLecture, resumeLecture, startLecture } from '@/api/lecture'
import { askText } from '@/api/qa'
import { LECTURE_STATE, LECTURE_STATUS_MAP, normalizeLectureStatus } from '@/constants/lecture'
import { useLectureStore } from '@/stores/lecture'
import audioPlayer from '@/utils/audioPlayer'
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
const playbackMode = ref('speech')
const audioCurrentTime = ref(0)
const audioDuration = ref(0)
const isAudioPaused = ref(false)
const isSpeechPaused = ref(false)

const failedAudioUrls = new Set()
const audioUnsubscribers = []

let speechUtterance = null

const lectureStatus = computed(() => normalizeLectureStatus(lectureStore.status))
const statusMeta = computed(
  () => LECTURE_STATUS_MAP[lectureStatus.value] || LECTURE_STATUS_MAP[LECTURE_STATE.IDLE],
)
const statusTone = computed(() => {
  const color = statusMeta.value.color
  if (color === 'default') {
    return 'neutral'
  }
  return color
})
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
const playbackModeLabel = computed(() => (playbackMode.value === 'audio' ? '音频播放' : '文本朗读'))
const audioProgressPercent = computed(() => {
  if (!audioDuration.value || playbackMode.value !== 'audio') {
    return 0
  }

  return Math.min(100, Math.max(0, (audioCurrentTime.value / audioDuration.value) * 100))
})
const canSeek = computed(() => playbackMode.value === 'audio' && audioDuration.value > 0)
const formattedCurrentTime = computed(() => formatDuration(audioCurrentTime.value))
const formattedDuration = computed(() => {
  if (!audioDuration.value) {
    return '--:--'
  }
  return formatDuration(audioDuration.value)
})

const formatDuration = seconds => {
  const value = Number(seconds)
  if (!Number.isFinite(value) || value < 0) {
    return '00:00'
  }

  const totalSeconds = Math.floor(value)
  const minutes = String(Math.floor(totalSeconds / 60)).padStart(2, '0')
  const remainSeconds = String(totalSeconds % 60).padStart(2, '0')
  return `${minutes}:${remainSeconds}`
}

const renderMd = text => {
  if (!text) {
    return ''
  }
  return marked.parse(text, { breaks: true })
}

const clearError = () => {
  lectureStore.setErrorMessage('')
}

const showError = (error, fallback) => {
  lectureStore.setErrorMessage(getErrorMessage(error, fallback))
}

const resetAudioProgress = () => {
  audioCurrentTime.value = 0
  audioDuration.value = 0
}

const resetPauseFlags = () => {
  isAudioPaused.value = false
  isSpeechPaused.value = false
}

const stopCurrentPlayback = () => {
  audioPlayer.stop()

  if (globalThis.speechSynthesis) {
    globalThis.speechSynthesis.cancel()
  }

  speechUtterance = null
  isSpeaking.value = false
  resetPauseFlags()
  resetAudioProgress()
}

const pauseCurrentPlayback = () => {
  if (playbackMode.value === 'audio' && (isSpeaking.value || audioCurrentTime.value > 0)) {
    audioPlayer.pause()
    isAudioPaused.value = true
    isSpeaking.value = false
    return
  }

  if (playbackMode.value === 'speech' && globalThis.speechSynthesis?.speaking) {
    globalThis.speechSynthesis.pause()
    isSpeechPaused.value = true
    isSpeaking.value = false
  }
}

const resumeCurrentPlayback = async () => {
  if (playbackMode.value === 'audio' && isAudioPaused.value) {
    try {
      await audioPlayer.resume()
      isAudioPaused.value = false
      isSpeaking.value = true
      lectureStore.setStatus(LECTURE_STATE.PLAYING)
      return
    } catch (error) {
      showError(error, '浏览器阻止了自动播放，请点击“开始朗读”继续。')
      return
    }
  }

  if (playbackMode.value === 'speech' && isSpeechPaused.value && globalThis.speechSynthesis?.paused) {
    globalThis.speechSynthesis.resume()
    isSpeechPaused.value = false
    isSpeaking.value = true
    lectureStore.setStatus(LECTURE_STATE.PLAYING)
  }
}

const speakWithBrowser = text => {
  if (!text || !globalThis.speechSynthesis) {
    return
  }

  playbackMode.value = 'speech'
  resetAudioProgress()
  resetPauseFlags()

  speechUtterance = new SpeechSynthesisUtterance(text)
  speechUtterance.lang = 'zh-CN'
  speechUtterance.rate = 1
  speechUtterance.onend = () => {
    isSpeaking.value = false
    resetPauseFlags()
  }
  speechUtterance.onerror = () => {
    isSpeaking.value = false
    resetPauseFlags()
  }

  globalThis.speechSynthesis.speak(speechUtterance)
  isSpeaking.value = true
  lectureStore.setStatus(LECTURE_STATE.PLAYING)
}

const fallbackToSpeech = text => {
  stopCurrentPlayback()
  speakWithBrowser(text)
}

const playAudioForSlide = async slide => {
  if (!slide?.audioUrl || failedAudioUrls.has(slide.audioUrl)) {
    fallbackToSpeech(slide?.content || '')
    return
  }

  playbackMode.value = 'audio'
  resetPauseFlags()

  try {
    await audioPlayer.load(slide.audioUrl)
    audioDuration.value = audioPlayer.getDuration()
    audioCurrentTime.value = audioPlayer.getCurrentTime()
    await audioPlayer.play()
    isSpeaking.value = true
    lectureStore.setStatus(LECTURE_STATE.PLAYING)
  } catch (error) {
    if (error?.name === 'NotAllowedError') {
      showError(error, '浏览器阻止了自动播放，请点击“开始朗读”继续。')
      stopCurrentPlayback()
      return
    }

    failedAudioUrls.add(slide.audioUrl)
    showError('音频加载失败，已切换文本朗读', '音频加载失败，已切换文本朗读')
    fallbackToSpeech(slide.content)
  }
}

const togglePlayback = async () => {
  if (!currentSlide.value) {
    return
  }

  if (isSpeaking.value) {
    stopCurrentPlayback()
    return
  }

  if (playbackMode.value === 'audio' && isAudioPaused.value) {
    await resumeCurrentPlayback()
    return
  }

  if (playbackMode.value === 'speech' && isSpeechPaused.value) {
    await resumeCurrentPlayback()
    return
  }

  stopCurrentPlayback()

  if (currentSlide.value.audioUrl && !failedAudioUrls.has(currentSlide.value.audioUrl)) {
    await playAudioForSlide(currentSlide.value)
    return
  }

  fallbackToSpeech(currentSlide.value.content)
}

const normalizeSlide = (segment, index) => ({
  id: segment?.id || segment?.nodeId || `segment-${index + 1}`,
  nodeId: segment?.nodeId || segment?.id || `node-${index + 1}`,
  pageIndex: Number(segment?.pageIndex || index + 1),
  title: segment?.title || `第 ${index + 1} 页`,
  content: segment?.content || '',
  knowledgePoints: Array.isArray(segment?.knowledgePoints) ? segment.knowledgePoints : [],
  audioUrl: segment?.audioUrl || null,
})

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
    const segments = Array.isArray(response.data?.segments) ? response.data.segments : []

    if (!segments.length) {
      slides.value = []
      showError(null, '讲稿为空，请先生成讲稿后再进入课堂。')
      return false
    }

    slides.value = segments.map(normalizeSlide)
    lectureStore.setCurrentPage(1)
    syncCurrentNodeWithSlide(1)
    playbackMode.value = slides.value[0]?.audioUrl ? 'audio' : 'speech'
    return true
  } catch (error) {
    slides.value = []
    showError(error, '无法加载课堂讲稿，请稍后重试。')
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
    showError(error, '无法开始课堂，请稍后重试。')
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
  const shouldResumeAfterFailure = isSpeaking.value
  pauseCurrentPlayback()

  try {
    const response = await pauseLecture(lectureStore.sessionId)
    lectureStore.setStatus(response.data?.status)
  } catch (error) {
    showError(error, '暂停课堂失败，请稍后重试。')
    if (shouldResumeAfterFailure) {
      await resumeCurrentPlayback()
    }
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
    await resumeCurrentPlayback()
  } catch (error) {
    showError(error, '继续课堂失败，请稍后重试。')
  } finally {
    lectureStore.setLoading(false)
  }
}

const previousSlide = () => {
  if (currentPage.value <= 1) {
    return
  }

  stopCurrentPlayback()
  lectureStore.setCurrentPage(currentPage.value - 1)
  syncCurrentNodeWithSlide(currentPage.value)
}

const nextSlide = () => {
  if (currentPage.value >= totalPages.value) {
    stopCurrentPlayback()
    return
  }

  stopCurrentPlayback()
  lectureStore.setCurrentPage(currentPage.value + 1)
  syncCurrentNodeWithSlide(currentPage.value)
}

const handleSeek = event => {
  if (!canSeek.value) {
    return
  }

  const rect = event.currentTarget.getBoundingClientRect()
  const ratio = Math.min(Math.max((event.clientX - rect.left) / rect.width, 0), 1)
  const targetTime = audioDuration.value * ratio
  audioPlayer.seek(targetTime)
  audioCurrentTime.value = audioPlayer.getCurrentTime()
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
    answer: '正在思考中...',
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
    qaItem.answer = response.data?.answer || '当前没有获取到有效回答。'
    qaItem.evidence = Array.isArray(response.data?.evidence) ? response.data.evidence : []
  } catch (error) {
    const message = getErrorMessage(error, '提问失败，请稍后重试。')
    qaItem.answer = message
    showError(error, '提问失败，请稍后重试。')
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

watch(
  () => currentSlide.value?.id,
  () => {
    stopCurrentPlayback()
    playbackMode.value = currentSlide.value?.audioUrl ? 'audio' : 'speech'
  },
)

onMounted(async () => {
  audioUnsubscribers.push(
    audioPlayer.onEnded(() => {
      isSpeaking.value = false
      isAudioPaused.value = false
      audioCurrentTime.value = audioPlayer.getCurrentTime()
    }),
  )
  audioUnsubscribers.push(
    audioPlayer.onTimeUpdate(() => {
      audioCurrentTime.value = audioPlayer.getCurrentTime()
      audioDuration.value = audioPlayer.getDuration()
    }),
  )
  audioUnsubscribers.push(
    audioPlayer.onError(() => {
      if (playbackMode.value === 'audio' && isSpeaking.value && currentSlide.value?.content) {
        if (currentSlide.value.audioUrl) {
          failedAudioUrls.add(currentSlide.value.audioUrl)
        }
        showError('音频加载失败，已切换文本朗读', '音频加载失败，已切换文本朗读')
        fallbackToSpeech(currentSlide.value.content)
        return
      }

      isSpeaking.value = false
    }),
  )

  lectureStore.reset()
  lectureStore.setCoursewareId(coursewareId)
  const loaded = await loadSlides()
  if (loaded) {
    await startLectureSession()
  }
})

onUnmounted(() => {
  stopCurrentPlayback()
  audioUnsubscribers.forEach(unsubscribe => unsubscribe())
  audioPlayer.destroy()
  lectureStore.reset()
})
</script>

<style scoped>
.lecture-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(320px, 0.95fr);
  gap: 1.25rem;
  align-items: start;
}

.lecture-main,
.lecture-chat-panel {
  min-height: 42rem;
}

.lecture-main {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.lecture-topbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  padding: 1.5rem 1.6rem;
  border-radius: calc(var(--radius-xl) + 0.15rem);
  background:
    radial-gradient(circle at top left, rgba(123, 110, 255, 0.18), transparent 34%),
    rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(126, 136, 184, 0.14);
  box-shadow: var(--shadow-md);
}

.lecture-topbar h1 {
  margin: 0;
  font-size: clamp(1.8rem, 3vw, 2.5rem);
  line-height: 1.08;
  letter-spacing: -0.03em;
}

.lecture-subtitle {
  margin: 0.9rem 0 0;
  color: var(--text-secondary);
  line-height: 1.7;
}

.lecture-topbar__meta {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.lecture-page-pill {
  display: inline-flex;
  align-items: center;
  min-height: 1.95rem;
  padding: 0.35rem 0.7rem;
  border-radius: 999px;
  background: rgba(95, 104, 255, 0.08);
  color: var(--primary-color);
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.lecture-script-card {
  min-height: 26rem;
}

.script-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.script-card__label-group {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.audio-tag {
  display: inline-flex;
  align-items: center;
  min-height: 2rem;
  padding: 0.35rem 0.75rem;
  border-radius: 999px;
  background: rgba(103, 118, 139, 0.12);
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.audio-tag.ready {
  background: rgba(31, 157, 103, 0.12);
  color: var(--success-color);
}

.page-progress {
  min-width: 4.75rem;
  text-align: right;
  color: var(--text-tertiary);
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.script-card__content p {
  margin: 1rem 0 0;
  color: var(--text-secondary);
  line-height: 2;
  white-space: pre-wrap;
  font-size: 1.02rem;
}

.knowledge-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  margin-top: 1.25rem;
}

.knowledge-tag {
  display: inline-flex;
  align-items: center;
  min-height: 2rem;
  padding: 0.35rem 0.8rem;
  border-radius: 999px;
  background: rgba(95, 104, 255, 0.08);
  color: var(--primary-color);
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.lecture-control-card {
  display: flex;
  flex-direction: column;
  gap: 1.2rem;
}

.control-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
}

.control-group {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.control-label {
  color: var(--text-tertiary);
  font-size: var(--font-size-xs);
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.control-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.timeline {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.audio-progress-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
}

.audio-progress-header strong {
  display: block;
  margin-top: 0.35rem;
  color: var(--text-primary);
  font-size: 1rem;
}

.audio-time {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.audio-progress-track {
  position: relative;
  width: 100%;
  height: 0.7rem;
  border-radius: 999px;
  overflow: hidden;
  background: rgba(126, 136, 166, 0.14);
  cursor: pointer;
}

.audio-progress-track.disabled {
  cursor: default;
}

.audio-progress-fill {
  position: absolute;
  inset: 0 auto 0 0;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--primary-color), var(--secondary-color));
}

.timeline-meta {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.lecture-chat-panel {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.chat-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.chat-header h2 {
  margin: 0;
  font-size: 1.45rem;
}

.chat-header__status {
  color: var(--text-tertiary);
  font-size: var(--font-size-sm);
  white-space: nowrap;
}

.chat-history {
  flex: 1;
  min-height: 18rem;
  max-height: 38rem;
  overflow-y: auto;
  padding-right: 0.35rem;
}

.chat-empty {
  display: grid;
  place-items: center;
  min-height: 100%;
  text-align: center;
  color: var(--text-secondary);
}

.chat-empty h3 {
  margin: 0;
  color: var(--text-primary);
}

.chat-empty p {
  margin: 0.7rem 0 0;
  line-height: 1.8;
}

.chat-turn {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  margin-bottom: 1rem;
}

.bubble {
  max-width: 100%;
  padding: 1rem 1.05rem;
  border-radius: 1.2rem;
  box-shadow: var(--shadow-sm);
}

.bubble--user {
  align-self: flex-end;
  max-width: 84%;
  border-top-right-radius: 0.5rem;
  background: linear-gradient(135deg, rgba(95, 104, 255, 0.96), rgba(141, 91, 255, 0.92));
  color: #ffffff;
}

.bubble--assistant {
  align-self: flex-start;
  max-width: 92%;
  border-top-left-radius: 0.5rem;
  background: rgba(248, 250, 255, 0.94);
  border: 1px solid rgba(129, 140, 183, 0.12);
}

.bubble-role {
  display: inline-block;
  margin-bottom: 0.55rem;
  font-size: var(--font-size-xs);
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.bubble p {
  margin: 0;
  line-height: 1.8;
}

.bubble-answer {
  color: var(--text-secondary);
  line-height: 1.8;
}

.bubble-answer :deep(p) {
  margin: 0.45rem 0;
}

.bubble-answer :deep(code) {
  padding: 0.1rem 0.35rem;
  border-radius: 0.35rem;
  background: rgba(95, 104, 255, 0.08);
}

.bubble-answer :deep(pre) {
  margin: 0.65rem 0;
  padding: 0.9rem;
  border-radius: var(--radius-md);
  background: rgba(238, 242, 255, 0.78);
  overflow-x: auto;
}

.evidence-panel {
  margin-top: 0.8rem;
}

.evidence-panel summary {
  cursor: pointer;
  color: var(--primary-color);
  font-weight: 600;
}

.evidence-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin-top: 0.75rem;
}

.evidence-item {
  padding: 0.85rem;
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(127, 138, 177, 0.12);
}

.evidence-item__meta {
  display: flex;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.45rem;
  color: var(--text-tertiary);
  font-size: var(--font-size-xs);
}

.evidence-item p {
  color: var(--text-secondary);
}

.chat-composer {
  display: flex;
  gap: 0.75rem;
  align-items: center;
}

.chat-composer > .app-input {
  flex: 1;
}

.toast {
  position: fixed;
  right: 1.25rem;
  bottom: 1.25rem;
  z-index: 40;
  display: flex;
  align-items: center;
  gap: 1rem;
  max-width: min(26rem, calc(100vw - 2rem));
  padding: 0.95rem 1rem;
  border-radius: var(--radius-md);
  background: rgba(203, 65, 94, 0.97);
  color: #ffffff;
  box-shadow: var(--shadow-md);
}

.toast button {
  color: inherit;
  font-weight: 700;
  cursor: pointer;
}

@media (max-width: 1180px) {
  .lecture-layout {
    grid-template-columns: 1fr;
  }

  .lecture-chat-panel {
    min-height: auto;
  }
}

@media (max-width: 768px) {
  .lecture-topbar {
    padding: 1.35rem;
    flex-direction: column;
  }

  .lecture-topbar__meta {
    justify-content: flex-start;
  }

  .control-grid {
    grid-template-columns: 1fr;
  }

  .audio-progress-header,
  .timeline-meta,
  .chat-composer {
    flex-direction: column;
    align-items: stretch;
  }

  .bubble--user,
  .bubble--assistant {
    max-width: 100%;
  }
}

@media (max-width: 640px) {
  .toast {
    left: 1rem;
    right: 1rem;
    max-width: none;
  }
}
</style>
