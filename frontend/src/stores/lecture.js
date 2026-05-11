import { defineStore } from 'pinia'
import { ref } from 'vue'
import { LECTURE_STATE, normalizeLectureStatus } from '@/constants/lecture'

export const useLectureStore = defineStore('lecture', () => {
  const sessionId = ref('')
  const coursewareId = ref('')
  const status = ref(LECTURE_STATE.IDLE)
  const currentPage = ref(1)
  const currentNode = ref(null)
  const breakpointTime = ref(0)
  const breakpointPage = ref(null)
  const currentQuestion = ref('')
  const currentAnswer = ref('')
  const vadEnabled = ref(false)
  const wsConnected = ref(false)
  const isRecording = ref(false)
  const isStreamingAnswer = ref(false)
  const audioMode = ref('speech')
  const lastRecognizedText = ref('')
  const errorMessage = ref('')
  const isLoading = ref(false)

  const setSession = session => {
    sessionId.value = session?.sessionId || session?.id || ''
    coursewareId.value = session?.coursewareId || coursewareId.value || ''
  }

  const setCoursewareId = id => {
    coursewareId.value = id || ''
  }

  const setStatus = nextStatus => {
    status.value = normalizeLectureStatus(nextStatus)
  }

  const setCurrentPage = page => {
    const nextPage = Number(page)
    currentPage.value = Number.isFinite(nextPage) && nextPage > 0 ? nextPage : 1
  }

  const setCurrentNode = node => {
    currentNode.value = node || null
    if (node?.pageIndex) {
      setCurrentPage(node.pageIndex)
    }
  }

  const setBreakpoint = (pageIndex, currentTime) => {
    const nextPage = Number(pageIndex)
    breakpointPage.value = Number.isFinite(nextPage) && nextPage > 0 ? nextPage : null

    const nextTime = Number(currentTime)
    breakpointTime.value = Number.isFinite(nextTime) && nextTime >= 0 ? nextTime : 0
  }

  const clearBreakpoint = () => {
    breakpointPage.value = null
    breakpointTime.value = 0
  }

  const pauseForInterrupt = (currentTime, pageIndex) => {
    setBreakpoint(pageIndex, currentTime)
    if (breakpointPage.value) {
      setCurrentPage(breakpointPage.value)
    }
    setStatus(LECTURE_STATE.INTERRUPTED)
  }

  const enterAnswering = question => {
    currentQuestion.value = String(question || '').trim()
    currentAnswer.value = ''
    isStreamingAnswer.value = true
    setStatus(LECTURE_STATE.ANSWERING)
  }

  const appendAnswerDelta = delta => {
    currentAnswer.value += delta || ''
  }

  const finishAnswer = () => {
    isStreamingAnswer.value = false
  }

  const resumeFromBreakpoint = () => {
    if (breakpointPage.value) {
      setCurrentPage(breakpointPage.value)
    }
    setStatus(LECTURE_STATE.RESUMING)
  }

  const setVadEnabled = enabled => {
    vadEnabled.value = Boolean(enabled)
  }

  const setWsConnected = connected => {
    wsConnected.value = Boolean(connected)
  }

  const setRecording = recording => {
    isRecording.value = Boolean(recording)
  }

  const setStreamingAnswer = streaming => {
    isStreamingAnswer.value = Boolean(streaming)
  }

  const setAudioMode = mode => {
    audioMode.value = mode === 'audio' ? 'audio' : 'speech'
  }

  const setLastRecognizedText = text => {
    lastRecognizedText.value = text || ''
  }

  const setErrorMessage = message => {
    errorMessage.value = message || ''
  }

  const setLoading = loading => {
    isLoading.value = Boolean(loading)
  }

  const syncFromStartResponse = data => {
    setSession({
      sessionId: data?.sessionId,
      coursewareId: data?.coursewareId || coursewareId.value,
    })
    setStatus(data?.status)
    setCurrentNode(data?.currentNode || null)
  }

  const reset = () => {
    sessionId.value = ''
    coursewareId.value = ''
    status.value = LECTURE_STATE.IDLE
    currentPage.value = 1
    currentNode.value = null
    breakpointTime.value = 0
    breakpointPage.value = null
    currentQuestion.value = ''
    currentAnswer.value = ''
    vadEnabled.value = false
    wsConnected.value = false
    isRecording.value = false
    isStreamingAnswer.value = false
    audioMode.value = 'speech'
    lastRecognizedText.value = ''
    errorMessage.value = ''
    isLoading.value = false
  }

  return {
    sessionId,
    coursewareId,
    status,
    currentPage,
    currentNode,
    breakpointTime,
    breakpointPage,
    currentQuestion,
    currentAnswer,
    vadEnabled,
    wsConnected,
    isRecording,
    isStreamingAnswer,
    audioMode,
    lastRecognizedText,
    errorMessage,
    isLoading,
    setSession,
    setCoursewareId,
    setStatus,
    setCurrentPage,
    setCurrentNode,
    pauseForInterrupt,
    enterAnswering,
    appendAnswerDelta,
    finishAnswer,
    resumeFromBreakpoint,
    setVadEnabled,
    setWsConnected,
    setRecording,
    setStreamingAnswer,
    setBreakpoint,
    clearBreakpoint,
    setAudioMode,
    setLastRecognizedText,
    setErrorMessage,
    setLoading,
    syncFromStartResponse,
    reset,
  }
})
