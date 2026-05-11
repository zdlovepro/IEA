<template>
  <div class="upload-page">
    <section class="page-shell page-section upload-hero">
      <div>
        <span class="eyebrow">课件上传</span>
        <h1 class="page-title">上传课件</h1>
        <p class="page-description">
          选择课件后，系统会自动完成解析、讲稿生成与课堂资源准备。你可以随时回到课程列表继续查看进度。
        </p>
      </div>
    </section>

    <section class="page-shell upload-layout">
      <AppCard class="upload-main-card" tone="accent">
        <FileUpload
          :disabled="uploadStatus?.status === 'uploading'"
          @file-selected="handleFileSelected"
          @error="handleError"
        />

        <div class="upload-status-panel">
          <div class="status-summary">
            <div>
              <p class="status-label">当前状态</p>
              <h3>{{ currentStatusTitle }}</h3>
              <p class="status-text">{{ currentStatusMessage }}</p>
            </div>
            <StatusBadge :label="currentStatusBadge.text" :tone="currentStatusBadge.tone" />
          </div>

          <div class="progress-track" v-if="showProgress">
            <div class="progress-fill" :style="{ width: `${uploadStatus.progress || 0}%` }"></div>
          </div>

          <div v-if="latestCourseware" class="next-actions">
            <AppButton variant="secondary" size="sm" @click="openCourseware(latestCourseware)">
              查看讲稿
            </AppButton>
            <AppButton
              size="sm"
              :disabled="latestCourseware.status !== 'READY'"
              @click="enterLecture(latestCourseware)"
            >
              进入课堂
            </AppButton>
          </div>
        </div>
      </AppCard>

      <div class="upload-side-column">
        <AppCard tone="glass" class="side-card">
          <h3>上传流程</h3>
          <ol class="flow-list">
            <li>上传课件文件</li>
            <li>解析页面与文本结构</li>
            <li>生成讲稿与语音资源</li>
            <li>进入互动课堂继续教学</li>
          </ol>
        </AppCard>

        <AppCard tone="subtle" class="side-card">
          <h3>准备建议</h3>
          <ul class="tip-list">
            <li>优先上传排版完整的 PDF 或 PPTX。</li>
            <li>章节标题明确时，讲稿结构会更稳定。</li>
            <li>上传后可以先检查讲稿，再进入课堂。</li>
          </ul>
        </AppCard>
      </div>
    </section>

    <section class="page-shell page-section">
      <div class="section-header">
        <div>
          <span class="eyebrow">最近课件</span>
          <h2 class="page-title section-title">继续处理最近上传的课程</h2>
        </div>
      </div>

      <div v-if="uploadError" class="inline-error">
        <span>{{ uploadError }}</span>
        <div class="inline-error__actions">
          <button v-if="canRetryUpload" @click="retryLastUpload">重新上传</button>
          <button @click="uploadError = null">关闭</button>
        </div>
      </div>

      <div v-if="uploadedCourseware.length" class="grid-auto">
        <CoursewareCard
          v-for="item in uploadedCourseware"
          :key="item.id"
          :courseware="item"
          @view-script="openCourseware"
          @enter-lecture="enterLecture"
        />
      </div>
      <AppCard v-else tone="glass">
        <EmptyState
          title="还没有上传记录"
          description="上传一个课件后，这里会显示最近的课程资源与解析进度。"
        />
      </AppCard>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import FileUpload from '@/components/Upload/FileUpload.vue'
import CoursewareCard from '@/components/course/CoursewareCard.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppCard from '@/components/ui/AppCard.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { getCoursewareDetail, listCourseware, uploadCourseware } from '@/api/courseware'
import { getCoursewareStatusMeta } from '@/constants/courseware'
import { useCourseStore } from '@/stores/course'
import { getErrorMessage } from '@/utils'

const router = useRouter()
const courseStore = useCourseStore()

const uploadStatus = ref(null)
const uploadError = ref(null)
const uploadedCourseware = ref([])
const latestCoursewareId = ref('')
const lastSelectedFile = ref(null)

let pollTimer = null

const latestCourseware = computed(() =>
  uploadedCourseware.value.find(item => item.id === latestCoursewareId.value) || uploadedCourseware.value[0] || null,
)

const currentStatusTitle = computed(() => {
  if (!uploadStatus.value) {
    return '未选择文件'
  }

  if (uploadStatus.value.status === 'uploading') {
    return '上传中'
  }

  if (uploadStatus.value.status === 'success') {
    return '处理进行中'
  }

  return '上传失败'
})

const currentStatusMessage = computed(() => {
  return uploadStatus.value?.message || '选择一个课件后，系统会在这里展示上传与解析进度。'
})

const currentStatusBadge = computed(() => {
  if (!uploadStatus.value) {
    return { text: '未开始', tone: 'neutral' }
  }

  if (uploadStatus.value.status === 'uploading') {
    return { text: '上传中', tone: 'accent' }
  }

  if (uploadStatus.value.status === 'success') {
    return { text: '处理中', tone: 'success' }
  }

  return { text: '失败', tone: 'danger' }
})

const showProgress = computed(() => uploadStatus.value && uploadStatus.value.progress !== undefined)
const canRetryUpload = computed(
  () => Boolean(lastSelectedFile.value) && uploadStatus.value?.status !== 'uploading',
)

const showError = (error, fallback) => {
  uploadError.value = getErrorMessage(error, fallback)
}

const normalizeCoursewareItem = item => ({
  id: item?.id || item?.coursewareId || '',
  name: item?.name || '未命名课程',
  status: item?.status || 'UPLOADED',
  createdAt: item?.createdAt || item?.updatedAt || new Date().toISOString(),
  updatedAt: item?.updatedAt || item?.createdAt || '',
  currentTaskStatus: item?.currentTaskStatus || '',
})

const upsertCourseware = courseware => {
  const normalized = normalizeCoursewareItem(courseware)
  const index = uploadedCourseware.value.findIndex(item => item.id === normalized.id)

  if (index >= 0) {
    uploadedCourseware.value[index] = {
      ...uploadedCourseware.value[index],
      ...normalized,
    }
    return uploadedCourseware.value[index]
  }

  uploadedCourseware.value.unshift(normalized)
  return normalized
}

const pollParseStatus = coursewareId => {
  const maxAttempts = 30
  let attempts = 0

  if (pollTimer) {
    clearInterval(pollTimer)
  }

  pollTimer = setInterval(async () => {
    attempts += 1
    if (attempts > maxAttempts) {
      clearInterval(pollTimer)
      uploadStatus.value = { status: 'error', message: '解析超时，请稍后再试。' }
      const courseware = latestCourseware.value
      if (courseware) {
        courseware.status = 'FAILED'
      }
      return
    }

    try {
      const response = await getCoursewareDetail(coursewareId)
      const courseware = upsertCourseware({
        id: response.data?.coursewareId || coursewareId,
        name: response.data?.name || latestCourseware.value?.name,
        status: response.data?.status,
        createdAt: response.data?.createdAt || latestCourseware.value?.createdAt,
        updatedAt: response.data?.updatedAt,
        currentTaskStatus: response.data?.currentTaskStatus,
      })

      if (courseware.status === 'PARSED' || courseware.status === 'READY') {
        clearInterval(pollTimer)
        uploadStatus.value = {
          status: 'success',
          message: courseware.status === 'READY' ? '课件已就绪，可以查看讲稿或进入课堂。' : '解析完成，正在准备讲稿。',
          progress: 100,
        }
      } else if (courseware.status === 'FAILED') {
        clearInterval(pollTimer)
        uploadStatus.value = { status: 'error', message: '课件处理失败，请重新上传。' }
      } else {
        const statusMeta = getCoursewareStatusMeta(courseware.status)
        uploadStatus.value = {
          status: 'success',
          message: `当前进度：${statusMeta.text}`,
          progress: uploadStatus.value?.progress ?? 100,
        }
      }
    } catch {
      // 轮询期间的瞬时错误不打断主流程
    }
  }, 3000)
}

const handleFileSelected = async file => {
  if (uploadStatus.value?.status === 'uploading') {
    return
  }

  lastSelectedFile.value = file

  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }

  uploadError.value = null
  uploadStatus.value = {
    status: 'uploading',
    message: '正在上传课件...',
    progress: 0,
  }

  try {
    const response = await uploadCourseware(file, file.name, {
      timeout: 120000,
      onUploadProgress: progressEvent => {
        const loaded = progressEvent.loaded
        const total = progressEvent.total
        const hasValidTotal = Number.isFinite(total) && total > 0

        if (hasValidTotal) {
          const percent = Math.round((loaded / total) * 100)
          uploadStatus.value = {
            status: 'uploading',
            message: `正在上传课件... ${percent}%`,
            progress: percent,
          }
          return
        }

        uploadStatus.value = {
          status: 'uploading',
          message: `正在上传课件... 已上传 ${loaded.toLocaleString()} 字节`,
          progress: uploadStatus.value?.progress ?? 0,
        }
      },
    })

    const coursewareId = response.data?.coursewareId
    latestCoursewareId.value = coursewareId
    const courseware = upsertCourseware({
      id: coursewareId,
      name: file.name,
      status: 'PARSING',
      createdAt: new Date().toISOString(),
    })

    courseStore.addCourseware(courseware)
    uploadStatus.value = {
      status: 'success',
      message: '上传成功，系统正在解析课件。',
      progress: 100,
    }

    pollParseStatus(coursewareId)
  } catch (error) {
    uploadStatus.value = { status: 'error', message: '上传失败，请稍后重试。' }
    showError(error, '上传失败，请稍后重试。')
  }
}

const retryLastUpload = async () => {
  if (!lastSelectedFile.value) {
    return
  }
  await handleFileSelected(lastSelectedFile.value)
}

const handleError = error => {
  showError(error, '文件校验失败，请检查格式后重试。')
}

const openCourseware = courseware => {
  courseStore.setCourseware(courseware)
  router.push({ name: 'Script', params: { coursewareId: courseware.id } })
}

const enterLecture = courseware => {
  router.push({ name: 'Lecture', params: { coursewareId: courseware.id } })
}

const loadCoursewareList = async () => {
  try {
    const response = await listCourseware()
    if (Array.isArray(response.data?.items)) {
      uploadedCourseware.value = response.data.items.map(normalizeCoursewareItem)
    }
  } catch {
    // 列表加载失败不影响上传主流程
  }
}

onMounted(() => {
  loadCoursewareList()
})

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
  }
})
</script>

<style scoped>
.upload-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) 320px;
  gap: 1.25rem;
}

.section-title {
  font-size: clamp(1.8rem, 3vw, 2.4rem);
}

.upload-main-card {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.upload-status-panel {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1.25rem;
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.76);
  border: 1px solid rgba(131, 141, 184, 0.12);
}

.status-summary {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.status-label {
  margin: 0;
  color: var(--text-tertiary);
  font-size: var(--font-size-xs);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.status-summary h3 {
  margin: 0.35rem 0 0;
  font-size: 1.25rem;
}

.status-text {
  margin: 0.55rem 0 0;
  color: var(--text-secondary);
  line-height: 1.7;
}

.progress-track {
  width: 100%;
  height: 0.65rem;
  border-radius: 999px;
  overflow: hidden;
  background: rgba(123, 133, 159, 0.14);
}

.progress-fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--primary-color), var(--accent-color));
  transition: width 0.3s ease;
}

.next-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.upload-side-column {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.side-card h3 {
  margin: 0 0 1rem;
  font-size: 1.1rem;
}

.flow-list,
.tip-list {
  margin: 0;
  padding-left: 1.1rem;
  color: var(--text-secondary);
  line-height: 1.9;
}

.inline-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1rem;
  padding: 0.95rem 1rem;
  border-radius: var(--radius-md);
  background: rgba(230, 84, 106, 0.08);
  border: 1px solid rgba(230, 84, 106, 0.15);
  color: #b93f59;
}

.inline-error__actions {
  display: inline-flex;
  align-items: center;
  gap: 0.75rem;
}

.inline-error button {
  color: inherit;
  font-weight: 600;
  cursor: pointer;
}

@media (max-width: 1024px) {
  .upload-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .status-summary {
    flex-direction: column;
  }

  .next-actions {
    flex-direction: column;
  }
}
</style>
