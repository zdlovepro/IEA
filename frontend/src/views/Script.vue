<template>
  <div class="script-page">
    <section class="page-shell page-section">
      <div class="section-header">
        <div>
          <span class="eyebrow">讲稿检查</span>
          <h1 class="page-title">课件讲稿</h1>
          <p class="page-description">
            在进入课堂前，先检查逐页讲稿、知识点与语音资源是否准备完成。
          </p>
        </div>

        <div class="header-actions">
          <AppButton variant="secondary" @click="goBack">返回课程列表</AppButton>
          <AppButton
            v-if="!scriptData && scriptStatus !== 'GENERATING_SCRIPT'"
            @click="handleGenerateScript"
          >
            生成讲稿
          </AppButton>
          <AppButton
            v-else
            :disabled="!scriptData || scriptStatus === 'GENERATING_SCRIPT'"
            @click="startLecturePage"
          >
            进入课堂
          </AppButton>
        </div>
      </div>
    </section>

    <section class="page-shell page-section">
      <div class="script-layout">
        <AppCard class="script-outline-card" tone="glass">
          <div class="outline-header">
            <div>
              <p class="outline-label">课件信息</p>
              <h2>{{ scriptData?.coursewareId || coursewareId }}</h2>
            </div>
            <StatusBadge :label="statusMeta.text" :tone="statusMeta.tone" />
          </div>

          <div v-if="scriptData?.outline?.length" class="outline-list">
            <button
              v-for="(item, index) in scriptData.outline"
              :key="item.id"
              class="outline-item"
              :class="{ active: activeSegmentId === item.id }"
              @click="scrollToSegment(item.id)"
            >
              <span>{{ index + 1 }}</span>
              <strong>{{ item.title }}</strong>
            </button>
          </div>
          <div v-else class="outline-empty">
            讲稿生成后，这里会显示逐页导航和页面结构。
          </div>
        </AppCard>

        <div class="script-main">
          <div v-if="loading" class="surface-panel state-card">正在加载讲稿...</div>

          <AppCard v-else-if="scriptStatus === 'GENERATING_SCRIPT'" class="state-card" tone="glass">
            <div class="generating-state">
              <span class="spinner"></span>
              <div>
                <h3>讲稿生成中</h3>
                <p>系统正在准备 opening、逐页讲解、过渡语和收尾内容，请稍候。</p>
              </div>
            </div>
          </AppCard>

          <template v-else-if="scriptData">
            <AppCard v-if="scriptData.opening" class="intro-card" tone="subtle">
              <span class="pill">Opening</span>
              <p>{{ scriptData.opening }}</p>
            </AppCard>

            <div class="segment-list" ref="segmentsRef">
              <AppCard
                v-for="segment in scriptData.segments"
                :key="segment.id"
                :id="`segment-${segment.id}`"
                class="segment-card"
                :class="{ active: activeSegmentId === segment.id }"
                tone="glass"
              >
                <div class="segment-card__header">
                  <div>
                    <span class="pill">第 {{ segment.pageIndex }} 页</span>
                    <h3>{{ segment.title }}</h3>
                  </div>
                  <StatusBadge
                    :label="segment.audioUrl ? '音频已就绪' : '待朗读'"
                    :tone="segment.audioUrl ? 'success' : 'info'"
                  />
                </div>

                <div class="segment-card__content">
                  <p>{{ segment.content }}</p>
                </div>

                <div v-if="segment.knowledgePoints?.length" class="tag-list">
                  <span v-for="point in segment.knowledgePoints" :key="point" class="tag-chip">
                    {{ point }}
                  </span>
                </div>
              </AppCard>
            </div>

            <AppCard v-if="scriptData.closing" class="intro-card" tone="subtle">
              <span class="pill">Closing</span>
              <p>{{ scriptData.closing }}</p>
            </AppCard>
          </template>

          <AppCard v-else tone="glass">
            <EmptyState
              title="暂无讲稿，请先生成讲稿。"
              description="系统会基于解析结果自动生成可检查、可朗读的逐页讲稿内容。"
              action-label="生成讲稿"
              @action="handleGenerateScript"
            />
          </AppCard>
        </div>
      </div>
    </section>

    <div v-if="errorMsg" class="toast" @click="errorMsg = ''">
      <span>{{ errorMsg }}</span>
      <button>关闭</button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppButton from '@/components/ui/AppButton.vue'
import AppCard from '@/components/ui/AppCard.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { generateScript, getCoursewareScript } from '@/api/courseware'
import { getCoursewareStatusMeta } from '@/constants/courseware'
import { getErrorMessage } from '@/utils'

const router = useRouter()
const route = useRoute()

const loading = ref(true)
const scriptData = ref(null)
const scriptStatus = ref('')
const activeSegmentId = ref('')
const errorMsg = ref('')
const segmentsRef = ref(null)

let pollTimer = null

const coursewareId = route.params.coursewareId

const statusMeta = computed(() => {
  if (scriptStatus.value === 'GENERATING_SCRIPT') {
    return getCoursewareStatusMeta('GENERATING_SCRIPT')
  }

  return getCoursewareStatusMeta(scriptStatus.value || 'READY')
})

const normalizeScript = raw => {
  const segments = Array.isArray(raw?.segments)
    ? raw.segments.map((segment, index) => ({
        id: segment?.id || segment?.nodeId || `segment-${index + 1}`,
        nodeId: segment?.nodeId || segment?.id || `node-${index + 1}`,
        pageIndex: Number(segment?.pageIndex || index + 1),
        title: segment?.title || `第 ${index + 1} 页`,
        content: segment?.content || '',
        knowledgePoints: Array.isArray(segment?.knowledgePoints) ? segment.knowledgePoints : [],
        audioUrl: segment?.audioUrl || null,
      }))
    : []

  const outline = Array.isArray(raw?.outline) && raw.outline.length
    ? raw.outline.map((item, index) => ({
        id: item?.id || segments[index]?.id || `outline-${index + 1}`,
        title: item?.title || segments[index]?.title || `第 ${index + 1} 页`,
      }))
    : segments.map(segment => ({
        id: segment.id,
        title: segment.title,
      }))

  return {
    coursewareId: raw?.coursewareId || coursewareId,
    opening: raw?.opening || '',
    closing: raw?.closing || '',
    status: raw?.status || 'READY',
    outline,
    segments,
  }
}

const showError = (error, fallback) => {
  errorMsg.value = getErrorMessage(error, fallback)
}

const fetchScript = async () => {
  loading.value = true
  try {
    const response = await getCoursewareScript(coursewareId)
    const raw = response.data
    const normalized = raw ? normalizeScript(raw) : null

    if (normalized?.segments?.length) {
      scriptData.value = normalized
      scriptStatus.value = normalized.status
      activeSegmentId.value = normalized.outline[0]?.id || normalized.segments[0]?.id || ''
    } else {
      scriptData.value = null
      scriptStatus.value = raw?.status || ''
    }
  } catch (error) {
    scriptData.value = null
    showError(error, '无法获取讲稿，请稍后重试。')
  } finally {
    loading.value = false
  }
}

const handleGenerateScript = async () => {
  scriptStatus.value = 'GENERATING_SCRIPT'
  errorMsg.value = ''

  try {
    await generateScript(coursewareId)
    pollGenerateStatus()
  } catch (error) {
    scriptStatus.value = ''
    showError(error, '生成讲稿失败，请稍后重试。')
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
      scriptStatus.value = ''
      errorMsg.value = '讲稿生成超时，请稍后再试。'
      return
    }

    try {
      const response = await getCoursewareScript(coursewareId)
      const raw = response.data
      const normalized = raw ? normalizeScript(raw) : null

      if (normalized?.segments?.length) {
        clearInterval(pollTimer)
        scriptData.value = normalized
        scriptStatus.value = normalized.status
        activeSegmentId.value = normalized.outline[0]?.id || normalized.segments[0]?.id || ''
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
  router.push('/courses')
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
.script-layout {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 1.25rem;
}

.header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.script-outline-card {
  position: sticky;
  top: 6rem;
  height: fit-content;
}

.outline-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.outline-label {
  margin: 0;
  color: var(--text-tertiary);
  font-size: var(--font-size-xs);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.outline-header h2 {
  margin: 0.4rem 0 0;
  font-size: 1.15rem;
  word-break: break-all;
}

.outline-list {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  margin-top: 1.2rem;
}

.outline-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.8rem 0.9rem;
  border-radius: var(--radius-md);
  border: 1px solid rgba(132, 143, 184, 0.12);
  background: rgba(248, 250, 255, 0.86);
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--transition-base),
    background var(--transition-base),
    transform var(--transition-base);
}

.outline-item:hover,
.outline-item.active {
  transform: translateY(-1px);
  border-color: rgba(95, 104, 255, 0.22);
  background: rgba(95, 104, 255, 0.08);
}

.outline-item span {
  width: 1.75rem;
  height: 1.75rem;
  display: grid;
  place-items: center;
  border-radius: 0.75rem;
  background: rgba(95, 104, 255, 0.12);
  color: var(--primary-color);
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.outline-item strong {
  font-size: var(--font-size-sm);
  color: var(--text-primary);
}

.outline-empty,
.state-card {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 14rem;
  color: var(--text-secondary);
  text-align: center;
}

.script-main {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.intro-card p {
  margin: 0.9rem 0 0;
  color: var(--text-secondary);
  line-height: 1.9;
  white-space: pre-wrap;
}

.segment-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.segment-card {
  scroll-margin-top: 7rem;
}

.segment-card.active {
  border-color: rgba(95, 104, 255, 0.24);
  box-shadow: 0 20px 44px rgba(95, 104, 255, 0.12);
}

.segment-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.segment-card__header h3 {
  margin: 0.8rem 0 0;
  font-size: 1.35rem;
}

.segment-card__content p {
  margin: 1rem 0 0;
  color: var(--text-secondary);
  line-height: 1.95;
  white-space: pre-wrap;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  margin-top: 1rem;
}

.tag-chip {
  display: inline-flex;
  align-items: center;
  min-height: 2rem;
  padding: 0.4rem 0.8rem;
  border-radius: 999px;
  background: rgba(95, 104, 255, 0.08);
  color: var(--primary-color);
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.generating-state {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.generating-state h3 {
  margin: 0;
}

.generating-state p {
  margin: 0.5rem 0 0;
  line-height: 1.7;
}

.spinner {
  width: 2.4rem;
  height: 2.4rem;
  flex-shrink: 0;
  border-radius: 50%;
  border: 3px solid rgba(95, 104, 255, 0.14);
  border-top-color: var(--primary-color);
  animation: spin 0.8s linear infinite;
}

.toast {
  position: fixed;
  right: 1.25rem;
  bottom: 1.25rem;
  display: flex;
  align-items: center;
  gap: 1rem;
  max-width: min(24rem, calc(100vw - 2rem));
  padding: 0.95rem 1rem;
  border-radius: var(--radius-md);
  background: rgba(203, 65, 94, 0.96);
  color: #ffffff;
  box-shadow: var(--shadow-md);
  cursor: pointer;
  z-index: 40;
}

.toast button {
  color: inherit;
  font-weight: 700;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1080px) {
  .script-layout {
    grid-template-columns: 1fr;
  }

  .script-outline-card {
    position: static;
  }
}

@media (max-width: 768px) {
  .header-actions {
    width: 100%;
  }

  .header-actions > * {
    flex: 1;
  }

  .segment-card__header {
    flex-direction: column;
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
