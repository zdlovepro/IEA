/**
 * 使用 store 管理上传和讲课状态
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useCoursStore = defineStore('cours', () => {
  // 课件信息
  const coursewareList = ref([])
  const currentCourseware = ref(null)

  // 当前讲课会话
  const currentSession = ref(null)
  const sessionProgress = ref(0)

  // 问答相关
  const qaHistory = ref([])
  const isWaitingForAnswer = ref(false)

  // 计算属性
  const hasCourseware = computed(() => coursewareList.value.length > 0)
  const isLectureActive = computed(() => currentSession.value !== null)

  // 方法
  const addCourseware = courseware => {
    coursewareList.value.push({
      id: Date.now(),
      ...courseware,
      createdAt: new Date().toISOString(),
    })
  }

  const setCourseware = courseware => {
    currentCourseware.value = courseware
  }

  const createSession = sessionData => {
    currentSession.value = {
      id: Date.now(),
      ...sessionData,
      startedAt: new Date().toISOString(),
      status: 'playing',
    }
  }

  const updateSessionProgress = progress => {
    sessionProgress.value = Math.min(100, Math.max(0, progress))
  }

  const addQARecord = record => {
    qaHistory.value.push({
      id: Date.now(),
      ...record,
      timestamp: new Date().toISOString(),
    })
  }

  const clearSession = () => {
    currentSession.value = null
    qaHistory.value = []
    sessionProgress.value = 0
  }

  return {
    // 状态
    coursewareList,
    currentCourseware,
    currentSession,
    sessionProgress,
    qaHistory,
    isWaitingForAnswer,

    // 计算属性
    hasCourseware,
    isLectureActive,

    // 方法
    addCourseware,
    setCourseware,
    createSession,
    updateSessionProgress,
    addQARecord,
    clearSession,
  }
})
