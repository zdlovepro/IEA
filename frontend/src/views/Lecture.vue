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
                  <span class="audio-tag" :class="{ ready: useAudioPlayback }">
                    {{ useAudioPlayback ? '优先使用后端音频资源' : '当前页使用文本朗读兜底' }}
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
                  <AppButton v-else variant="secondary" :disabled="true">
                    {{ statusMeta.text }}
                  </AppButton>
                </div>
              </div>
              <div class="control-group control-group--voice">
                <span class="control-label">语音打断</span>
                <div class="control-row">
                  <AppButton
                    v-if="!voiceInterruptEnabled"
                    variant="secondary"
                    :disabled="!canUseVoiceInterrupt || lectureStore.isLoading"
                    @click="enableVoiceInterrupt"
                  >
                    开启语音打断
                  </AppButton>
                  <AppButton v-else variant="secondary" @click="disableVoiceInterrupt">
                    关闭语音打断
                  </AppButton>
                </div>

                <div class="voice-status-row">
                  <span class="voice-status-pill" :class="voiceStatusClass">
                    {{ voiceStatusText }}
                  </span>
                  <span class="voice-status-hint">{{ voiceStatusHint }}</span>
                </div>

                <div class="voice-volume-meter" :class="{ active: voiceInterruptEnabled }">
                  <span class="voice-volume-bar" :style="{ transform: `scaleX(${voiceVolumeScale})` }"></span>
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
                <span>当前页进度 {{ currentPage }} / {{ totalPages || 0 }}</span>
                <span>{{ isContinuousPlayback ? '连续播放中' : '单页预览' }}</span>
                <span v-if="lectureStatus === LECTURE_STATE.ENDED">课程已结束</span>
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
                    <div
                      v-for="(evidence, index) in qa.evidence"
                      :key="`${qa.id}-${index}`"
                      class="evidence-item"
                    >
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
import { createLecturePlaybackEngine } from '@/utils/lecturePlaybackEngine'
import { createRecorder } from '@/utils/recorder'
import { createVAD } from '@/utils/vad'
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
const isContinuousPlayback = ref(false)
const voiceInterruptEnabled = ref(false)
const canUseVoiceInterrupt = ref(false)
const voiceInterruptState = ref('off')
const voiceInterruptHint = ref('开启后会在检测到学生说话后自动打断课堂')
const voiceVolume = ref(0)
const recordedAudioBlob = ref(null)
const interruptBreakpointTime = ref(0)
const isVadListening = ref(false)
const isVoiceRecording = ref(false)

const VOICE_INTERRUPT_STATE = {
  OFF: 'off',
  LISTENING: 'listening',
  RECORDING: 'recording',
  COMPLETED: 'completed',
  UNAVAILABLE: 'unavailable',
}

const failedAudioUrls = new Set()
const audioUnsubscribers = []
const recorder = createRecorder()

let speechUtterance = null
let manualSpeechStopRequested = false
let playbackEngine = null
let vad = null

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
const useAudioPlayback = computed(
  () => Boolean(currentSlide.value?.audioUrl) && !failedAudioUrls.has(currentSlide.value.audioUrl),
)
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
  if (playbackMode.value !== 'audio' || !audioDuration.value) {
    return '--:--'
  }
  return formatDuration(audioDuration.value)
})
const voiceStatusText = computed(() => {
  switch (voiceInterruptState.value) {
    case VOICE_INTERRUPT_STATE.LISTENING:
      return '正在倾听'
    case VOICE_INTERRUPT_STATE.RECORDING:
      return '正在录音'
    case VOICE_INTERRUPT_STATE.COMPLETED:
      return '录音完成'
    case VOICE_INTERRUPT_STATE.UNAVAILABLE:
      return '麦克风不可用'
    default:
      return voiceInterruptEnabled.value ? '已开启' : '未开启'
  }
})
const voiceStatusHint = computed(() => voiceInterruptHint.value)
const voiceStatusClass = computed(() => `voice-status-pill--${voiceInterruptState.value}`)
const voiceVolumeScale = computed(() => {
  if (!voiceInterruptEnabled.value) {
    return 0.04
  }

  return Math.min(1, Math.max(0.06, voiceVolume.value * 14))
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

const supportsVoiceInterrupt = () =>
  Boolean(globalThis.navigator?.mediaDevices?.getUserMedia) &&
  typeof globalThis.MediaRecorder !== 'undefined' &&
  Boolean(globalThis.AudioContext || globalThis.webkitAudioContext)

const updateVoiceInterruptState = (state, hint) => {
  voiceInterruptState.value = state
  if (hint !== undefined) {
    voiceInterruptHint.value = hint
  }
}

const stopVadMonitoring = () => {
  vad?.stop()
  isVadListening.value = false
  voiceVolume.value = 0
}

const handleMicrophoneError = error => {
  const permissionDenied =
    error?.name === 'NotAllowedError' ||
    error?.name === 'PermissionDeniedError' ||
    error?.message?.includes('权限')

  const unsupported = error?.message?.includes('不支持')
  const message = permissionDenied
    ? '无法访问麦克风，请检查浏览器权限，或手动输入问题。'
    : unsupported
      ? '当前浏览器不支持语音打断，请改用手动输入问题。'
      : getErrorMessage(error, '无法启用语音打断，请稍后重试。')

  updateVoiceInterruptState(
    unsupported ? VOICE_INTERRUPT_STATE.UNAVAILABLE : VOICE_INTERRUPT_STATE.OFF,
    message,
  )
  voiceInterruptEnabled.value = false
  showError(message, message)
}

const ensureVad = () => {
  if (vad) {
    return vad
  }

  vad = createVAD({
    threshold: 0.04,
    silenceDurationMs: 2000,
    minSpeechDurationMs: 160,
    getStream: () => recorder.requestMicrophone(),
    onSpeechStart: () => {
      void handleSpeechStart()
    },
    onSpeechEnd: () => {
      void handleSpeechEnd()
    },
    onVolumeChange: volume => {
      voiceVolume.value = volume
    },
  })

  return vad
}

const beginVoiceInterruptMonitoring = async ({ force = false } = {}) => {
  if (!voiceInterruptEnabled.value || !canUseVoiceInterrupt.value || lectureStatus.value === LECTURE_STATE.ENDED) {
    return false
  }

  if (isVoiceRecording.value) {
    return false
  }

  if (force && voiceInterruptState.value === VOICE_INTERRUPT_STATE.COMPLETED) {
    updateVoiceInterruptState(VOICE_INTERRUPT_STATE.OFF, '语音打断已恢复，正在重新倾听')
  }

  try {
    await recorder.requestMicrophone()
    await ensureVad().start()
    isVadListening.value = true
    if (!isVoiceRecording.value) {
      updateVoiceInterruptState(
        VOICE_INTERRUPT_STATE.LISTENING,
        '正在倾听，检测到说话后会自动打断课堂',
      )
    }
    return true
  } catch (error) {
    stopVadMonitoring()
    handleMicrophoneError(error)
    return false
  }
}

const enableVoiceInterrupt = async () => {
  clearError()

  if (!canUseVoiceInterrupt.value) {
    handleMicrophoneError(new Error('当前浏览器不支持语音打断，请改用手动输入问题。'))
    return
  }

  voiceInterruptEnabled.value = true
  recordedAudioBlob.value = null
  interruptBreakpointTime.value = 0
  await beginVoiceInterruptMonitoring({ force: true })
}

const disableVoiceInterrupt = async () => {
  voiceInterruptEnabled.value = false
  stopVadMonitoring()

  if (isVoiceRecording.value) {
    try {
      await recorder.stopRecording()
    } catch (error) {
      showError(error, '关闭语音打断时停止录音失败，请稍后重试。')
    }
  }

  isVoiceRecording.value = false
  recordedAudioBlob.value = null
  updateVoiceInterruptState(VOICE_INTERRUPT_STATE.OFF, '语音打断已关闭')
}

const resetAudioProgress = () => {
  audioCurrentTime.value = 0
  audioDuration.value = 0
}

const resetPauseFlags = () => {
  isAudioPaused.value = false
  isSpeechPaused.value = false
}

const haltPlayback = () => {
  audioPlayer.stop()

  if (globalThis.speechSynthesis) {
    if (globalThis.speechSynthesis.speaking || globalThis.speechSynthesis.pending || globalThis.speechSynthesis.paused) {
      manualSpeechStopRequested = true
      globalThis.speechSynthesis.cancel()
    }
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

const handleSpeechStart = async () => {
  if (!voiceInterruptEnabled.value || isVoiceRecording.value || !currentSlide.value) {
    return
  }

  if (lectureStatus.value === LECTURE_STATE.ENDED || lectureStatus.value === LECTURE_STATE.ANSWERING) {
    return
  }

  stopVadMonitoring()
  interruptBreakpointTime.value =
    playbackMode.value === 'audio' ? audioPlayer.getCurrentTime() : audioCurrentTime.value
  pauseCurrentPlayback()
  lectureStore.setStatus(LECTURE_STATE.INTERRUPTED)

  try {
    await recorder.startRecording()
    isVoiceRecording.value = true
    updateVoiceInterruptState(
      VOICE_INTERRUPT_STATE.RECORDING,
      '正在倾听，请继续说出你的问题',
    )
  } catch (error) {
    voiceInterruptEnabled.value = false
    updateVoiceInterruptState(VOICE_INTERRUPT_STATE.OFF, '语音打断已关闭')
    showError(error, '录音启动失败，请稍后重试。')
  }
}

const handleSpeechEnd = async () => {
  if (!isVoiceRecording.value) {
    return
  }

  try {
    const blob = await recorder.stopRecording()
    recordedAudioBlob.value = blob
    updateVoiceInterruptState(
      VOICE_INTERRUPT_STATE.COMPLETED,
      blob?.size ? '录音完成，等待识别' : '未采集到有效音频，请重试',
    )
  } catch (error) {
    voiceInterruptEnabled.value = false
    updateVoiceInterruptState(VOICE_INTERRUPT_STATE.OFF, '语音打断已关闭')
    showError(error, '录音停止失败，请稍后重试。')
  } finally {
    isVoiceRecording.value = false
    isVadListening.value = false
    voiceVolume.value = 0
  }
}

const resumeCurrentPlayback = async () => {
  if (playbackMode.value === 'audio' && isAudioPaused.value) {
    try {
      await audioPlayer.resume()
      isAudioPaused.value = false
      isSpeaking.value = true
      lectureStore.setStatus(LECTURE_STATE.PLAYING)
      return true
    } catch (error) {
      showError(error, '浏览器阻止了自动播放，请点击“开始朗读”继续。')
      return false
    }
  }

  if (playbackMode.value === 'speech' && isSpeechPaused.value && globalThis.speechSynthesis?.paused) {
    globalThis.speechSynthesis.resume()
    isSpeechPaused.value = false
    isSpeaking.value = true
    lectureStore.setStatus(LECTURE_STATE.PLAYING)
    return true
  }

  return false
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

const syncToPage = page => {
  lectureStore.setCurrentPage(page)
  syncCurrentNodeWithSlide(page)
  playbackMode.value = slides.value[page - 1]?.audioUrl ? 'audio' : 'speech'
}

const speakWithBrowser = text => {
  if (!text || !globalThis.speechSynthesis) {
    return
  }

  playbackMode.value = 'speech'
  resetAudioProgress()
  resetPauseFlags()
  manualSpeechStopRequested = false

  speechUtterance = new SpeechSynthesisUtterance(text)
  speechUtterance.lang = 'zh-CN'
  speechUtterance.rate = 1
  speechUtterance.onend = async () => {
    const manualStop = manualSpeechStopRequested
    manualSpeechStopRequested = false
    isSpeaking.value = false
    resetPauseFlags()

    if (manualStop) {
      return
    }

    await playbackEngine?.handlePlaybackEnded()
  }
  speechUtterance.onerror = () => {
    manualSpeechStopRequested = false
    isSpeaking.value = false
    resetPauseFlags()
  }

  globalThis.speechSynthesis.speak(speechUtterance)
  isSpeaking.value = true
  lectureStore.setStatus(LECTURE_STATE.PLAYING)
}

const fallbackToSpeech = text => {
  haltPlayback()
  speakWithBrowser(text)
}

const playCurrentSlideByPageIndex = async pageIndex => {
  const slide = slides.value[pageIndex - 1]
  if (!slide?.content) {
    return false
  }

  haltPlayback()

  if (!slide.audioUrl || failedAudioUrls.has(slide.audioUrl)) {
    fallbackToSpeech(slide.content)
    return true
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
    return true
  } catch (error) {
    if (error?.name === 'NotAllowedError') {
      showError(error, '浏览器阻止了自动播放，请点击“开始朗读”继续。')
      haltPlayback()
      return false
    }

    failedAudioUrls.add(slide.audioUrl)
    showError('音频加载失败，已切换文本朗读', '音频加载失败，已切换文本朗读')
    fallbackToSpeech(slide.content)
    return true
  }
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
    syncToPage(1)
    lectureStore.setStatus(LECTURE_STATE.IDLE)
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

const togglePlayback = async () => {
  if (!currentSlide.value) {
    return
  }

  if (isSpeaking.value || isAudioPaused.value || isSpeechPaused.value) {
    playbackEngine?.stopCurrentPage()
    stopVadMonitoring()
    if (voiceInterruptEnabled.value) {
      updateVoiceInterruptState(VOICE_INTERRUPT_STATE.OFF, '语音打断已开启，等待继续播放')
    }
    lectureStore.setStatus(LECTURE_STATE.IDLE)
    return
  }

  lectureStore.setStatus(LECTURE_STATE.PLAYING)
  await playbackEngine?.playPage(currentPage.value)
  if (voiceInterruptEnabled.value) {
    await beginVoiceInterruptMonitoring({ force: true })
  }
}

const handlePauseLecture = async () => {
  if (!lectureStore.sessionId) {
    return
  }

  lectureStore.setLoading(true)
  clearError()
  const hadActivePlayback =
    isSpeaking.value || isAudioPaused.value || isSpeechPaused.value || audioCurrentTime.value > 0
  pauseCurrentPlayback()
  stopVadMonitoring()
  if (voiceInterruptEnabled.value) {
    updateVoiceInterruptState(VOICE_INTERRUPT_STATE.OFF, '课堂已暂停，恢复后会重新倾听')
  }

  try {
    const response = await pauseLecture(lectureStore.sessionId)
    lectureStore.setStatus(response.data?.status)
  } catch (error) {
    showError(error, '暂停课堂失败，请稍后重试。')
    if (hadActivePlayback) {
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
    if (voiceInterruptEnabled.value) {
      await beginVoiceInterruptMonitoring({ force: true })
    }
  } catch (error) {
    showError(error, '继续课堂失败，请稍后重试。')
  } finally {
    lectureStore.setLoading(false)
  }
}

const previousSlide = async () => {
  if (currentPage.value <= 1) {
    return
  }

  const autoPlay = Boolean(playbackEngine?.isContinuousPlayback())
  await playbackEngine?.previousPage(autoPlay)
  if (!autoPlay) {
    stopVadMonitoring()
    if (voiceInterruptEnabled.value) {
      updateVoiceInterruptState(VOICE_INTERRUPT_STATE.OFF, '已切换页面，开始播放后会继续倾听')
    }
    lectureStore.setStatus(LECTURE_STATE.IDLE)
  } else if (voiceInterruptEnabled.value) {
    await beginVoiceInterruptMonitoring()
  }
}

const nextSlide = async () => {
  if (currentPage.value >= totalPages.value) {
    stopVadMonitoring()
    if (voiceInterruptEnabled.value) {
      updateVoiceInterruptState(VOICE_INTERRUPT_STATE.OFF, '课程已结束')
    }
    playbackEngine?.finishLecture()
    return
  }

  const autoPlay = Boolean(playbackEngine?.isContinuousPlayback())
  await playbackEngine?.nextPage(autoPlay)
  if (!autoPlay) {
    stopVadMonitoring()
    if (voiceInterruptEnabled.value) {
      updateVoiceInterruptState(VOICE_INTERRUPT_STATE.OFF, '已切换页面，开始播放后会继续倾听')
    }
    lectureStore.setStatus(LECTURE_STATE.IDLE)
  } else if (voiceInterruptEnabled.value) {
    await beginVoiceInterruptMonitoring()
  }
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
    resetAudioProgress()
    resetPauseFlags()
    isSpeaking.value = false
    playbackMode.value = useAudioPlayback.value ? 'audio' : 'speech'
  },
)

onMounted(async () => {
  canUseVoiceInterrupt.value = supportsVoiceInterrupt()
  updateVoiceInterruptState(
    canUseVoiceInterrupt.value ? VOICE_INTERRUPT_STATE.OFF : VOICE_INTERRUPT_STATE.UNAVAILABLE,
    canUseVoiceInterrupt.value
      ? '开启后会在检测到学生说话后自动打断课堂'
      : '当前浏览器不支持语音打断，请改用手动输入问题。',
  )

  playbackEngine = createLecturePlaybackEngine({
    getSlides: () => slides.value,
    getCurrentPage: () => currentPage.value,
    syncToPage,
    playCurrentPage: playCurrentSlideByPageIndex,
    stopPlayback: haltPlayback,
    setLectureStatus: status => lectureStore.setStatus(status),
    endedStatus: LECTURE_STATE.ENDED,
    onContinuousPlaybackChange: enabled => {
      isContinuousPlayback.value = enabled
    },
  })

  audioUnsubscribers.push(
    audioPlayer.onEnded(async () => {
      isSpeaking.value = false
      isAudioPaused.value = false
      audioCurrentTime.value = audioPlayer.getCurrentTime()
      await playbackEngine?.handlePlaybackEnded()
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
  playbackEngine?.stopCurrentPage()
  stopVadMonitoring()
  audioUnsubscribers.forEach(unsubscribe => unsubscribe())
  audioPlayer.destroy()
  recorder.destroy()
  void vad?.destroy()
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

.voice-status-row {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.voice-status-pill {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  min-height: 1.9rem;
  padding: 0.25rem 0.7rem;
  border-radius: 999px;
  background: rgba(126, 136, 166, 0.12);
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.voice-status-pill--listening {
  background: rgba(95, 104, 255, 0.12);
  color: var(--primary-color);
}

.voice-status-pill--recording {
  background: rgba(31, 157, 103, 0.14);
  color: var(--success-color);
}

.voice-status-pill--completed {
  background: rgba(228, 156, 49, 0.14);
  color: var(--warning-color);
}

.voice-status-pill--unavailable {
  background: rgba(203, 65, 94, 0.14);
  color: var(--error-color);
}

.voice-status-hint {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.voice-volume-meter {
  position: relative;
  width: 100%;
  height: 0.55rem;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(126, 136, 166, 0.12);
}

.voice-volume-meter.active {
  background: rgba(95, 104, 255, 0.12);
}

.voice-volume-bar {
  display: block;
  width: 100%;
  height: 100%;
  transform-origin: left center;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--primary-color), var(--secondary-color));
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
