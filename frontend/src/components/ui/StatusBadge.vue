<template>
  <span class="status-badge" :class="[toneClass, { compact }]">
    <slot>{{ label }}</slot>
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  label: {
    type: String,
    default: '',
  },
  tone: {
    type: String,
    default: 'neutral',
  },
  compact: {
    type: Boolean,
    default: false,
  },
})

const toneClass = computed(() => `tone-${props.tone}`)
</script>

<style scoped>
.status-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  min-height: 1.8rem;
  padding: 0.38rem 0.75rem;
  border-radius: 999px;
  font-size: 0.78rem;
  font-weight: 700;
  line-height: 1;
  letter-spacing: 0.01em;
  border: 1px solid transparent;
}

.status-badge.compact {
  min-height: 1.55rem;
  padding: 0.25rem 0.55rem;
  font-size: 0.72rem;
}

.tone-neutral {
  color: #516074;
  background: rgba(103, 118, 139, 0.12);
  border-color: rgba(103, 118, 139, 0.14);
}

.tone-info {
  color: #3457d5;
  background: rgba(52, 87, 213, 0.12);
  border-color: rgba(52, 87, 213, 0.15);
}

.tone-success {
  color: #18794e;
  background: rgba(24, 121, 78, 0.12);
  border-color: rgba(24, 121, 78, 0.15);
}

.tone-warning {
  color: #b26a0a;
  background: rgba(215, 142, 23, 0.16);
  border-color: rgba(215, 142, 23, 0.16);
}

.tone-danger {
  color: #cc415e;
  background: rgba(240, 74, 110, 0.12);
  border-color: rgba(240, 74, 110, 0.16);
}

.tone-accent {
  color: #6558f5;
  background: rgba(101, 88, 245, 0.12);
  border-color: rgba(101, 88, 245, 0.16);
}
</style>
