<template>
  <AppCard class="course-card" hoverable tone="glass">
    <div class="course-card__top">
      <div>
        <p class="course-card__eyebrow">课件资源</p>
        <h3 class="course-card__title">{{ courseware.name || '未命名课程' }}</h3>
      </div>
      <StatusBadge :label="statusMeta.text" :tone="statusMeta.tone" />
    </div>

    <div class="course-card__meta">
      <div class="meta-item">
        <span class="meta-label">上传时间</span>
        <span class="meta-value">{{ createdAtLabel }}</span>
      </div>
      <div class="meta-item">
        <span class="meta-label">任务状态</span>
        <span class="meta-value">{{ taskStatusLabel }}</span>
      </div>
    </div>

    <div class="course-card__actions">
      <AppButton variant="secondary" size="sm" @click="$emit('view-script', courseware)">
        查看讲稿
      </AppButton>
      <AppButton
        size="sm"
        :disabled="!canEnterLecture"
        @click="$emit('enter-lecture', courseware)"
      >
        进入课堂
      </AppButton>
    </div>
  </AppCard>
</template>

<script setup>
import { computed } from 'vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppCard from '@/components/ui/AppCard.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { getCoursewareStatusMeta, getTaskStatusLabel } from '@/constants/courseware'
import { formatDate } from '@/utils'

const props = defineProps({
  courseware: {
    type: Object,
    required: true,
  },
})

defineEmits(['view-script', 'enter-lecture'])

const statusMeta = computed(() => getCoursewareStatusMeta(props.courseware.status))

const createdAtLabel = computed(() => {
  const value = props.courseware.updatedAt || props.courseware.createdAt
  return value ? formatDate(value) : '刚刚上传'
})

const taskStatusLabel = computed(() => getTaskStatusLabel(props.courseware.currentTaskStatus))

const canEnterLecture = computed(() => props.courseware.status === 'READY')
</script>

<style scoped>
.course-card {
  display: flex;
  flex-direction: column;
  gap: 1.2rem;
  min-height: 15rem;
}

.course-card__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.course-card__eyebrow {
  margin: 0 0 0.45rem;
  color: var(--text-tertiary);
  font-size: var(--font-size-xs);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.course-card__title {
  margin: 0;
  font-size: 1.2rem;
  line-height: 1.35;
  color: var(--text-primary);
  word-break: break-word;
}

.course-card__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.9rem;
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding: 0.9rem;
  border-radius: var(--radius-md);
  background: rgba(248, 250, 255, 0.9);
  border: 1px solid rgba(148, 157, 188, 0.12);
}

.meta-label {
  font-size: var(--font-size-xs);
  color: var(--text-tertiary);
}

.meta-value {
  font-size: var(--font-size-sm);
  color: var(--text-primary);
  font-weight: 600;
}

.course-card__actions {
  display: flex;
  gap: 0.75rem;
  margin-top: auto;
}

.course-card__actions > * {
  flex: 1;
}

@media (max-width: 640px) {
  .course-card__meta {
    grid-template-columns: 1fr;
  }

  .course-card__actions {
    flex-direction: column;
  }
}
</style>
