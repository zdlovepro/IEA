<template>
  <div class="upload-area" @dragover.prevent @drop.prevent="handleDrop">
    <div class="upload-content">
      <div class="upload-icon">📄</div>
      <p class="upload-title">拖拽或点击上传课件</p>
      <p class="upload-desc">支持 PPT 和 PDF 格式，单个文件不超过 100MB</p>
      <input
        ref="fileInput"
        type="file"
        accept=".pptx,.pdf"
        hidden
        @change="handleFileSelect"
      />
      <button class="btn btn-primary" @click="selectFile">选择文件</button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ALLOWED_EXTENSIONS, MAX_FILE_SIZE, UPLOAD_STATUS } from '@/constans/upload'

const fileInput = ref(null)

const emit = defineEmits(['file-selected', 'error'])

const props = defineProps({
  disabled: {
    type: Boolean,
    default: false,
  },
})

const selectFile = () => {
  if (!props.disabled) {
    fileInput.value?.click()
  }
}

const validateFile = file => {
  // 检查文件类型
  const ext = '.' + file.name.split('.').pop().toLowerCase()
  if (!ALLOWED_EXTENSIONS.includes(ext)) {
    emit('error', `不支持的文件类型：${ext}`)
    return false
  }

  // 检查文件大小
  if (file.size > MAX_FILE_SIZE) {
    emit('error', `文件过大，请选择小于 100MB 的文件`)
    return false
  }

  return true
}

const handleFileSelect = event => {
  const file = event.target.files?.[0]
  if (file && validateFile(file)) {
    emit('file-selected', file)
  }
}

const handleDrop = event => {
  const file = event.dataTransfer.files?.[0]
  if (file && validateFile(file)) {
    emit('file-selected', file)
  }
}
</script>

<style scoped>
.upload-area {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 20rem;
  border: 2px dashed var(--primary-color);
  border-radius: var(--radius-lg);
  background-color: rgba(102, 126, 234, 0.05);
  cursor: pointer;
  transition: all var(--transition-base);
}

.upload-area:hover {
  border-color: var(--secondary-color);
  background-color: rgba(102, 126, 234, 0.1);
}

.upload-content {
  text-align: center;
  padding: 2rem;
}

.upload-icon {
  font-size: 3rem;
  margin-bottom: 1rem;
}

.upload-title {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--text-primary);
  margin: 0.5rem 0;
}

.upload-desc {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  margin: 0.5rem 0 1.5rem 0;
}

.btn {
  margin-top: 1rem;
}

/* 移动端适配 */
@media (max-width: 480px) {
  .upload-area {
    min-height: 15rem;
  }

  .upload-content {
    padding: 1.5rem;
  }

  .upload-icon {
    font-size: 2rem;
  }

  .upload-title {
    font-size: var(--font-size-md);
  }
}
</style>
