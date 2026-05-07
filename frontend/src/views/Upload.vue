<template>
  <div class="upload-page">
    <div class="upload-container">
      <h1>上传课件</h1>
      <p class="subtitle">支持 PPT 和 PDF，系统将自动解析并生成讲稿</p>

      <FileUpload
        :disabled="uploadStatus?.status === 'uploading'"
        @file-selected="handleFileSelected"
        @error="handleError"
      />

      <div v-if="uploadStatus" class="upload-status">
        <div class="status-indicator" :class="uploadStatus.status"></div>
        <p>{{ uploadStatus.message }}</p>
        <div v-if="uploadStatus.progress !== undefined" class="progress-bar">
          <div class="progress-fill" :style="{ width: uploadStatus.progress + '%' }"></div>
        </div>
      </div>

      <div v-if="uploadError" class="error-message">
        ⚠️ {{ uploadError }}
      </div>

      <div v-if="uploadedCourseware.length > 0" class="courseware-list">
        <h2>已上传课件</h2>
        <div class="list-items">
        <div
          v-for="item in uploadedCourseware"
          :key="item.id"
          class="list-item"
          @click="openCourseware(item)"
          >
            <div class="item-icon">📄</div>
            <div class="item-info">
              <p class="item-title">{{ item.name }}</p>
              <p class="item-meta">{{ formatDate(item.createdAt) }}</p>
            </div>
            <div class="item-status" :class="statusClass(item.status)">{{ statusLabel(item.status) }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import FileUpload from '@/components/Upload/FileUpload.vue'
import { useCourseStore } from '@/stores/course'
import { useRouter } from 'vue-router'
import { formatDate } from '@/utils'
import request from '@/utils/request'
import { COURSEWARE_API } from '@/constants/api'

const router = useRouter()
const courseStore = useCourseStore()

/** 当前上传状态（status / message / progress），为 null 时不展示任何状态提示 */
const uploadStatus = ref(null)
/** 错误文本，为 null 时不展示错误框 */
const uploadError = ref(null)
/** 已上传的课件列表（初始化时从后端加载，每次上传成功后头插） */
const uploadedCourseware = ref([])

let pollTimer = null

const statusClass = status => {
  switch (status) {
    case 'PARSING':
      return 'parsing'
    case 'PARSED':
    case 'READY':
      return 'success'
    case 'FAILED':
      return 'error'
    default:
      return ''
  }
}

const statusLabel = status => {
  switch (status) {
    case 'PARSING':
      return '解析中'
    case 'PARSED':
      return '已解析'
    case 'READY':
      return '可讲课'
    case 'FAILED':
      return '失败'
    default:
      return status || '未知状态'
  }
}

/**
 * 课件解析状态轮询
 * 上传成功后每 3s 轮询一次，最多 30 次（共 90s），解析完成或失败后停止
 * @param {string} coursewareId - 课件 ID
 * @param {object} coursewareItem - uploadedCourseware 列表中对应的引用，用于实时更新 status
 */
const pollParseStatus = (coursewareId, coursewareItem) => {
  const MAX_ATTEMPTS = 30
  let attempts = 0

  // 确保启动新轮询前清理旧 interval
  if (pollTimer) {
    clearInterval(pollTimer)
  }

  pollTimer = setInterval(async () => {
    attempts++
    if (attempts > MAX_ATTEMPTS) {
      clearInterval(pollTimer)
      coursewareItem.status = 'FAILED'
      uploadStatus.value = { status: 'error', message: '解析超时，请重试' }
      return
    }

    try {
      const res = await request.get(COURSEWARE_API.DETAIL(coursewareId))
      if (res.code === 0) {
        const status = res.data?.status
        coursewareItem.status = status
        if (status === 'PARSED' || status === 'READY') {
          clearInterval(pollTimer)
          uploadStatus.value = { status: 'success', message: '解析完成，可以开始讲课！' }
          setTimeout(() => { uploadStatus.value = null }, 3000)
        } else if (status === 'FAILED') {
          clearInterval(pollTimer)
          uploadStatus.value = { status: 'error', message: '课件解析失败，请重新上传' }
        }
      }
    } catch {
      // 网络错误时继续轮询
    }
  }, 3000)
}

/**
 * 处理文件选择事件（由 FileUpload 组件触发）
 * 流程：构建 FormData -> 带进度回调上传 -> 头插到列表 -> 启动解析状态轮询
 */
const handleFileSelected = async file => {
  // 防止并发上传：若当前正在上传则忽略新的选择事件
  if (uploadStatus.value?.status === 'uploading') return

  // 清理上一次遗留的解析轮询，避免旧 interval 持续运行造成状态串扰
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }

  uploadError.value = null
  uploadStatus.value = { status: 'uploading', message: '上传中...', progress: 0 }

  try {
    const formData = new FormData()
    formData.append('file', file)

    const res = await request.post(COURSEWARE_API.UPLOAD, formData, {
      timeout: 120000,
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: progressEvent => {
        const loaded = progressEvent.loaded
        const total = progressEvent.total
        const hasValidTotal = Number.isFinite(total) && total > 0

        if (hasValidTotal) {
          const percent = Math.round((loaded / total) * 100)
          uploadStatus.value = {
            status: 'uploading',
            message: `上传中... ${percent}%`,
            progress: percent,
          }
          return
        }

        uploadStatus.value = {
          status: 'uploading',
          message: `上传中... 已上传 ${loaded.toLocaleString()} 字节`,
          progress: uploadStatus.value?.progress ?? 0,
        }
      },
    })

    if (res.code !== 0) {
      throw new Error(res.message || '上传失败')
    }

    const coursewareId = res.data.coursewareId
    const coursewareItem = {
      id: coursewareId,
      name: file.name,
      size: file.size,
      status: 'PARSING',
      createdAt: new Date().toISOString(),
    }

    uploadedCourseware.value.unshift(coursewareItem)
    courseStore.addCourseware(coursewareItem)

    uploadStatus.value = { status: 'success', message: '上传成功，正在解析...', progress: 100 }

    pollParseStatus(coursewareId, coursewareItem)
  } catch (error) {
    uploadStatus.value = { status: 'error', message: '上传失败' }
    uploadError.value = error.message || '上传失败，请重试'
  }
}

/** FileUpload 组件校验失败时回调 */
const handleError = error => {
  uploadError.value = error
}

/** 跳转到请中课件的讲稿预览页 */
const openCourseware = courseware => {
  courseStore.setCourseware(courseware)
  router.push({ name: 'Script', params: { coursewareId: courseware.id } })
}

/**
 * 初始化加载课件列表
 * 接口未就绪时静默失败，不影响上传功能
 */
const loadCoursewareList = async () => {
  try {
    const res = await request.get(COURSEWARE_API.LIST)
    if (res.code === 0 && Array.isArray(res.data?.items)) {
      uploadedCourseware.value = res.data.items
    }
  } catch {
    // 接口未就绪时静默失败，不影响上传功能
  }
}

onMounted(() => {
  loadCoursewareList()
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.upload-page {
  padding: 2rem 1rem;
  max-width: 100%;
}

.upload-container {
  max-width: 800px;
  margin: 0 auto;
}

h1 {
  font-size: var(--font-size-2xl);
  margin-bottom: 0.5rem;
  color: var(--primary-color);
  text-align: center;
}

.subtitle {
  text-align: center;
  color: var(--text-secondary);
  margin-bottom: 2rem;
}

.upload-status {
  margin-top: 2rem;
  padding: 1.5rem;
  border-radius: var(--radius-md);
  background: var(--bg-secondary);
  text-align: center;
}

.status-indicator {
  display: inline-block;
  width: 1rem;
  height: 1rem;
  border-radius: 50%;
  margin-right: 0.5rem;
  animation: pulse 1s infinite;
}

.status-indicator.success {
  background: var(--success-color);
  animation: none;
}

.status-indicator.error {
  background: var(--error-color);
  animation: none;
}

.progress-bar {
  margin-top: 1rem;
  height: 0.5rem;
  background: var(--border-color);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--primary-color) 0%, var(--secondary-color) 100%);
  transition: width 0.3s ease;
}

.error-message {
  margin-top: 1rem;
  padding: 1rem;
  background: rgba(245, 34, 45, 0.1);
  border-left: 4px solid var(--error-color);
  border-radius: var(--radius-md);
  color: var(--error-color);
}

.courseware-list {
  margin-top: 3rem;
}

.courseware-list h2 {
  font-size: var(--font-size-lg);
  margin-bottom: 1rem;
  color: var(--primary-color);
}

.list-items {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 1rem;
}

.list-item {
  display: flex;
  align-items: center;
  padding: 1rem;
  background: white;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-base);
}

.list-item:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.item-icon {
  font-size: 2rem;
  margin-right: 1rem;
}

.item-info {
  flex: 1;
  min-width: 0;
}

.item-title {
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-meta {
  font-size: var(--font-size-sm);
  color: var(--text-hint);
  margin: 0.25rem 0 0 0;
}

.item-status {
  padding: 0.25rem 0.75rem;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.item-status.parsing {
  background: rgba(250, 173, 20, 0.1);
  color: var(--warning-color);
}

.item-status.success {
  background: rgba(82, 196, 26, 0.1);
  color: var(--success-color);
}

.item-status.error {
  background: rgba(245, 34, 45, 0.1);
  color: var(--error-color);
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}

/* 移动端适配 */
@media (max-width: 768px) {
  .upload-page {
    padding: 1.5rem 1rem;
  }

  h1 {
    font-size: var(--font-size-xl);
  }

  .list-items {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 480px) {
  .upload-page {
    padding: 1rem 0.75rem;
  }

  .upload-container {
    margin: 0;
  }

  .list-item {
    padding: 0.75rem;
  }

  .item-icon {
    font-size: 1.5rem;
  }
}
</style>
