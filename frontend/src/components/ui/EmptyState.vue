<template>
  <div class="empty-state">
    <div class="empty-orb"></div>
    <h3>{{ title }}</h3>
    <p>{{ description }}</p>
    <div v-if="$slots.actions || actionLabel" class="empty-actions">
      <slot name="actions">
        <AppButton v-if="actionLabel" @click="$emit('action')">
          {{ actionLabel }}
        </AppButton>
      </slot>
    </div>
  </div>
</template>

<script setup>
import AppButton from './AppButton.vue'

defineProps({
  title: {
    type: String,
    required: true,
  },
  description: {
    type: String,
    default: '',
  },
  actionLabel: {
    type: String,
    default: '',
  },
})

defineEmits(['action'])
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.9rem;
  min-height: 16rem;
  text-align: center;
  padding: 2rem 1rem;
}

.empty-orb {
  width: 4.5rem;
  height: 4.5rem;
  border-radius: 1.4rem;
  background:
    linear-gradient(135deg, rgba(92, 108, 255, 0.2), rgba(158, 119, 255, 0.12)),
    rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(118, 128, 255, 0.12);
  box-shadow: 0 18px 40px rgba(108, 117, 255, 0.18);
}

.empty-state h3 {
  font-size: 1.3rem;
  color: var(--text-primary);
}

.empty-state p {
  max-width: 32rem;
  color: var(--text-secondary);
  line-height: 1.7;
}

.empty-actions {
  margin-top: 0.35rem;
}
</style>
