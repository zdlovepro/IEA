<template>
  <section class="app-card" :class="[toneClass, { padded, hoverable }]">
    <slot />
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  tone: {
    type: String,
    default: 'default',
  },
  padded: {
    type: Boolean,
    default: true,
  },
  hoverable: {
    type: Boolean,
    default: false,
  },
})

const toneClass = computed(() => `tone-${props.tone}`)
</script>

<style scoped>
.app-card {
  position: relative;
  border: 1px solid rgba(145, 153, 183, 0.18);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.9);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
  transition:
    transform var(--transition-base),
    box-shadow var(--transition-base),
    border-color var(--transition-base);
}

.app-card.padded {
  padding: 1.5rem;
}

.app-card.hoverable:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
  border-color: rgba(115, 123, 255, 0.18);
}

.tone-glass {
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(18px);
}

.tone-accent {
  background:
    linear-gradient(180deg, rgba(244, 246, 255, 0.95), rgba(255, 255, 255, 0.92)),
    linear-gradient(135deg, rgba(117, 127, 255, 0.18), transparent 48%);
}

.tone-subtle {
  background: rgba(247, 249, 255, 0.92);
}
</style>
