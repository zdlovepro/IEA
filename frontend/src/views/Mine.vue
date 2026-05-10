<template>
  <div class="mine-page">
    <section class="page-shell page-section">
      <div class="profile-hero">
        <div class="profile-copy">
          <span class="eyebrow">个人工作台</span>
          <h1 class="page-title">我的课程</h1>
          <p class="page-description">
            这里集中展示你上传过的课程资源、当前处理进度和最近可继续的课堂入口。
          </p>
        </div>
        <AppButton size="lg" @click="router.push('/upload')">继续上传课件</AppButton>
      </div>
    </section>

    <section class="page-shell page-section">
      <div class="summary-grid">
        <AppCard v-for="card in summaryCards" :key="card.label" tone="glass" class="summary-card">
          <span>{{ card.label }}</span>
          <strong>{{ card.value }}</strong>
          <p>{{ card.description }}</p>
        </AppCard>
      </div>
    </section>

    <section class="page-shell page-section">
      <div class="section-header">
        <div>
          <span class="eyebrow">最近资源</span>
          <h2 class="page-title section-title">最近上传的课程</h2>
        </div>
      </div>

      <div v-if="errorMessage" class="inline-error">
        <span>{{ errorMessage }}</span>
        <button @click="errorMessage = ''">关闭</button>
      </div>

      <div v-if="recentCourses.length" class="course-grid">
        <CoursewareCard
          v-for="item in recentCourses"
          :key="item.id"
          :courseware="item"
          @view-script="openScript"
          @enter-lecture="enterLecture"
        />
      </div>

      <AppCard v-else tone="glass">
        <EmptyState
          title="还没有课程记录"
          description="上传第一份课件后，这里会开始展示你的课程统计和最近进度。"
          action-label="上传课件"
          @action="router.push('/upload')"
        />
      </AppCard>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CoursewareCard from '@/components/course/CoursewareCard.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppCard from '@/components/ui/AppCard.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { listCourseware } from '@/api/courseware'
import { getErrorMessage } from '@/utils'

const router = useRouter()

const coursewareList = ref([])
const errorMessage = ref('')

const summaryCards = computed(() => {
  const total = coursewareList.value.length
  const ready = coursewareList.value.filter(item => item.status === 'READY').length
  const running = coursewareList.value.filter(item =>
    ['PARSING', 'GENERATING_SCRIPT', 'UPLOADED', 'PARSED'].includes(item.status),
  ).length
  const failed = coursewareList.value.filter(item => item.status === 'FAILED').length

  return [
    { label: '课程数', value: total, description: '已上传并纳入管理的课件' },
    { label: '已就绪', value: ready, description: '可直接进入课堂的课程' },
    { label: '生成中', value: running, description: '等待解析或正在准备讲稿' },
    { label: '失败', value: failed, description: '需要重新处理的课程' },
  ]
})

const recentCourses = computed(() =>
  [...coursewareList.value].sort((left, right) => {
    const leftValue = new Date(left.updatedAt || left.createdAt || 0).getTime()
    const rightValue = new Date(right.updatedAt || right.createdAt || 0).getTime()
    return rightValue - leftValue
  }),
)

const normalizeCourseware = item => ({
  id: item?.id || item?.coursewareId || '',
  name: item?.name || '未命名课程',
  status: item?.status || 'UPLOADED',
  createdAt: item?.createdAt || '',
  updatedAt: item?.updatedAt || '',
  currentTaskStatus: item?.currentTaskStatus || '',
})

const loadCoursewareList = async () => {
  errorMessage.value = ''
  try {
    const response = await listCourseware()
    coursewareList.value = Array.isArray(response.data?.items)
      ? response.data.items.map(normalizeCourseware)
      : []
  } catch (error) {
    errorMessage.value = getErrorMessage(error, '加载我的课程失败，请稍后重试。')
  }
}

const openScript = courseware => {
  router.push({ name: 'Script', params: { coursewareId: courseware.id } })
}

const enterLecture = courseware => {
  router.push({ name: 'Lecture', params: { coursewareId: courseware.id } })
}

onMounted(() => {
  loadCoursewareList()
})
</script>

<style scoped>
.profile-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
  padding: 2rem;
  border-radius: calc(var(--radius-xl) + 0.15rem);
  background:
    radial-gradient(circle at top left, rgba(100, 112, 255, 0.18), transparent 34%),
    rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(123, 134, 182, 0.12);
  box-shadow: var(--shadow-md);
}

.summary-grid,
.course-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1rem;
}

.summary-card span {
  color: var(--text-tertiary);
  font-size: var(--font-size-xs);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.summary-card strong {
  display: block;
  margin-top: 0.45rem;
  font-size: 2rem;
}

.summary-card p {
  margin: 0.6rem 0 0;
  color: var(--text-secondary);
  line-height: 1.7;
}

.section-title {
  font-size: clamp(1.8rem, 3vw, 2.4rem);
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

.inline-error button {
  color: inherit;
  font-weight: 600;
  cursor: pointer;
}

@media (max-width: 1080px) {
  .summary-grid,
  .course-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .profile-hero {
    align-items: flex-start;
    flex-direction: column;
    padding: 1.35rem;
  }
}

@media (max-width: 640px) {
  .summary-grid,
  .course-grid {
    grid-template-columns: 1fr;
  }
}
</style>
