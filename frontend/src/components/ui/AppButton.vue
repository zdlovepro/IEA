<template>
  <button
    :type="type"
    class="app-button"
    :class="[`variant-${variant}`, `size-${size}`, { block, disabled }]"
    :disabled="disabled"
    @click="$emit('click', $event)"
  >
    <slot />
  </button>
</template>

<script setup>
defineProps({
  variant: {
    type: String,
    default: 'primary',
  },
  size: {
    type: String,
    default: 'md',
  },
  type: {
    type: String,
    default: 'button',
  },
  block: {
    type: Boolean,
    default: false,
  },
  disabled: {
    type: Boolean,
    default: false,
  },
})

defineEmits(['click'])
</script>

<style scoped>
.app-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  font-weight: 600;
  transition:
    transform var(--transition-fast),
    box-shadow var(--transition-base),
    background var(--transition-base),
    border-color var(--transition-base),
    color var(--transition-base);
  cursor: pointer;
  white-space: nowrap;
}

.app-button:hover:not(.disabled) {
  transform: translateY(-1px);
}

.app-button:active:not(.disabled) {
  transform: translateY(0);
}

.app-button.block {
  width: 100%;
}

.app-button.disabled {
  cursor: not-allowed;
  opacity: 0.6;
  transform: none;
  box-shadow: none;
}

.size-sm {
  min-height: 2.375rem;
  padding: 0.625rem 0.95rem;
  font-size: var(--font-size-sm);
}

.size-md {
  min-height: 2.875rem;
  padding: 0.8rem 1.25rem;
  font-size: var(--font-size-sm);
}

.size-lg {
  min-height: 3.25rem;
  padding: 0.95rem 1.35rem;
  font-size: var(--font-size-md);
}

.variant-primary {
  color: #ffffff;
  background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
  box-shadow: 0 14px 30px rgba(98, 103, 255, 0.24);
}

.variant-primary:hover:not(.disabled) {
  box-shadow: 0 18px 34px rgba(98, 103, 255, 0.32);
}

.variant-secondary {
  color: var(--text-primary);
  background: rgba(255, 255, 255, 0.88);
  border-color: var(--border-color);
  box-shadow: var(--shadow-sm);
}

.variant-secondary:hover:not(.disabled) {
  border-color: rgba(117, 127, 255, 0.26);
  box-shadow: var(--shadow-md);
}

.variant-ghost {
  color: var(--text-secondary);
  background: transparent;
  border-color: transparent;
}

.variant-ghost:hover:not(.disabled) {
  color: var(--text-primary);
  background: rgba(95, 104, 255, 0.08);
}

.variant-danger {
  color: #ffffff;
  background: linear-gradient(135deg, #ff6b78, #ff8b78);
  box-shadow: 0 14px 26px rgba(255, 107, 120, 0.22);
}
</style>
