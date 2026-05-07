/**
 * 课件与讲课会话的全局状态管理
 * 包含：课件列表、当前会话信息、皮期进度、问答历史
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useCourseStore = defineStore('course', () => {
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
  /**
   * 将新课件添加到列表，自动补充 id 和 createdAt
   * @param {object} courseware - 课件对象（来自上传接口返回或本地构建）
   */
  const addCourseware = courseware => {
    coursewareList.value.push({
      id: Date.now(),
      ...courseware,
      createdAt: new Date().toISOString(),
    })
  }

  /**
   * 设置当前操作中的课件，下游页面通过此字段获取课件信息
   */
  const setCourseware = courseware => {
    currentCourseware.value = courseware
  }

  /**
   * 创建讲课会话，自动补充 id、startedAt、初始状态
   * @param {object} sessionData - 会话创建参数（coursewareId 等）
   */
  const createSession = sessionData => {
    currentSession.value = {
      id: Date.now(),
      ...sessionData,
      startedAt: new Date().toISOString(),
      status: 'playing',
    }
  }

  /**
   * 更新讲课整体进度（超出 0~100 范围时自动截断）
   */
  const updateSessionProgress = progress => {
    sessionProgress.value = Math.min(100, Math.max(0, progress))
  }

  /**
   * 添加一条问答记录到历史列表。isWaitingForAnswer 由呼叫方自行维护
   */
  const addQARecord = record => {
    qaHistory.value.push({
      id: Date.now(),
      ...record,
      timestamp: new Date().toISOString(),
    })
  }

  /**
   * 清除当前会话（讲课结束或页面卡逃时调用）
   */
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
