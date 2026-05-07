import { defineStore } from 'pinia'
import { ref } from 'vue'
import { LECTURE_STATE, normalizeLectureStatus } from '@/constants/lecture'

export const useLectureStore = defineStore('lecture', () => {
  const sessionId = ref('')
  const coursewareId = ref('')
  const status = ref(LECTURE_STATE.IDLE)
  const currentPage = ref(1)
  const currentNode = ref(null)
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
    errorMessage.value = ''
    isLoading.value = false
  }

  return {
    sessionId,
    coursewareId,
    status,
    currentPage,
    currentNode,
    errorMessage,
    isLoading,
    setSession,
    setCoursewareId,
    setStatus,
    setCurrentPage,
    setCurrentNode,
    setErrorMessage,
    setLoading,
    syncFromStartResponse,
    reset,
  }
})
