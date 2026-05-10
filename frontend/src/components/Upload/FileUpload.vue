<template>
  <div
    class="upload-dropzone"
    :class="{ disabled, 'is-dragover': isDragOver }"
    @click="selectFile"
    @dragenter.prevent="handleDragEnter"
    @dragover.prevent="handleDragEnter"
    @dragleave.prevent="handleDragLeave"
    @drop.prevent="handleDrop"
  >
    <input
      ref="fileInput"
      type="file"
      :accept="acceptValue"
      hidden
      @change="handleFileSelect"
    />

    <div class="upload-visual">
      <span class="visual-ring visual-ring--primary"></span>
      <span class="visual-ring visual-ring--secondary"></span>
      <div class="upload-core">上传</div>
    </div>

    <div class="upload-copy">
      <h3>选择或拖拽课件到这里</h3>
      <p>支持 PDF、PPT、PPTX。推荐上传排版清晰的文件，以获得更稳定的解析效果。</p>
    </div>

    <div class="format-list">
      <span class="format-chip">PDF</span>
      <span class="format-chip">PPT</span>
      <span class="format-chip">PPTX</span>
      <span class="format-chip subtle">单文件不超过 100MB</span>
    </div>

    <AppButton :disabled="disabled" size="lg">
      {{ disabled ? '正在上传，请稍候' : '选择文件' }}
    </AppButton>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import AppButton from '@/components/ui/AppButton.vue'
import { ALLOWED_EXTENSIONS, MAX_FILE_SIZE } from '@/constants/upload'

const fileInput = ref(null)
const isDragOver = ref(false)

const emit = defineEmits(['file-selected', 'error'])

const props = defineProps({
  disabled: {
    type: Boolean,
    default: false,
  },
})

const acceptValue = computed(() => ALLOWED_EXTENSIONS.join(','))

const selectFile = () => {
  if (!props.disabled) {
    fileInput.value?.click()
  }
}

const validateFile = file => {
  if (props.disabled || !file) {
    return false
  }

  const extension = `.${file.name.split('.').pop()?.toLowerCase() || ''}`
  if (!ALLOWED_EXTENSIONS.includes(extension)) {
    emit('error', `暂不支持 ${extension} 文件，请上传 PDF、PPT 或 PPTX。`)
    return false
  }

  if (file.size > MAX_FILE_SIZE) {
    emit('error', '文件过大，请选择小于 100MB 的课件。')
    return false
  }

  return true
}

const handleFileSelect = event => {
  const file = event.target.files?.[0]
  if (validateFile(file)) {
    emit('file-selected', file)
  }
  event.target.value = ''
}

const handleDragEnter = () => {
  if (!props.disabled) {
    isDragOver.value = true
  }
}

const handleDragLeave = event => {
  if (event.currentTarget === event.target) {
    isDragOver.value = false
  }
}

const handleDrop = event => {
  isDragOver.value = false
  const file = event.dataTransfer?.files?.[0]
  if (validateFile(file)) {
    emit('file-selected', file)
  }
}
</script>

<style scoped>
.upload-dropzone {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.5rem;
  padding: 2.3rem 1.6rem;
  text-align: center;
  border: 1.5px dashed rgba(95, 104, 255, 0.25);
  border-radius: calc(var(--radius-xl) + 0.25rem);
  background:
    radial-gradient(circle at top, rgba(120, 108, 255, 0.16), transparent 34%),
    rgba(248, 250, 255, 0.92);
  cursor: pointer;
  transition:
    border-color var(--transition-base),
    transform var(--transition-base),
    box-shadow var(--transition-base),
    background var(--transition-base);
}

.upload-dropzone:hover,
.upload-dropzone.is-dragover {
  transform: translateY(-2px);
  border-color: rgba(95, 104, 255, 0.42);
  box-shadow: 0 22px 48px rgba(93, 104, 255, 0.14);
}

.upload-dropzone.disabled {
  cursor: not-allowed;
  opacity: 0.78;
  transform: none;
}

.upload-visual {
  position: relative;
  width: 9rem;
  height: 9rem;
  display: grid;
  place-items: center;
}

.visual-ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
}

.visual-ring--primary {
  background: radial-gradient(circle, rgba(95, 104, 255, 0.18), transparent 70%);
}

.visual-ring--secondary {
  inset: 0.85rem;
  border: 1px solid rgba(95, 104, 255, 0.16);
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(8px);
}

.upload-core {
  position: relative;
  width: 4.8rem;
  height: 4.8rem;
  display: grid;
  place-items: center;
  border-radius: 1.5rem;
  background: linear-gradient(135deg, var(--primary-color), var(--secondary-color));
  color: #ffffff;
  font-weight: 700;
  box-shadow: 0 14px 26px rgba(95, 104, 255, 0.24);
}

.upload-copy h3 {
  margin: 0;
  font-size: clamp(1.35rem, 3vw, 1.8rem);
}

.upload-copy p {
  margin: 0.75rem auto 0;
  max-width: 32rem;
  color: var(--text-secondary);
  line-height: 1.7;
}

.format-list {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.75rem;
}

.format-chip {
  display: inline-flex;
  align-items: center;
  min-height: 2rem;
  padding: 0.35rem 0.75rem;
  border-radius: 999px;
  background: rgba(95, 104, 255, 0.08);
  color: var(--primary-color);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.format-chip.subtle {
  background: rgba(123, 133, 159, 0.12);
  color: var(--text-secondary);
}

@media (max-width: 640px) {
  .upload-dropzone {
    padding: 1.8rem 1rem;
  }

  .upload-visual {
    width: 7.5rem;
    height: 7.5rem;
  }
}
</style>
