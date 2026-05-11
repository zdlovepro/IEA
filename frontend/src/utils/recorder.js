const DEFAULT_MIME_TYPE = 'audio/webm'

function resolveMimeType() {
  if (typeof globalThis.MediaRecorder === 'undefined') {
    return DEFAULT_MIME_TYPE
  }

  if (typeof globalThis.MediaRecorder.isTypeSupported !== 'function') {
    return DEFAULT_MIME_TYPE
  }

  if (globalThis.MediaRecorder.isTypeSupported('audio/webm;codecs=opus')) {
    return 'audio/webm;codecs=opus'
  }

  if (globalThis.MediaRecorder.isTypeSupported(DEFAULT_MIME_TYPE)) {
    return DEFAULT_MIME_TYPE
  }

  return ''
}

function normalizeRecorderError(error, fallback) {
  if (error instanceof Error && error.message) {
    return error
  }

  return new Error(fallback)
}

class Recorder {
  constructor() {
    this.stream = null
    this.mediaRecorder = null
    this.chunks = []
    this.recordedBlob = null
    this.mimeType = resolveMimeType()
  }

  ensureSupported() {
    if (!globalThis.navigator?.mediaDevices?.getUserMedia) {
      throw new Error('当前浏览器不支持麦克风访问')
    }

    if (typeof globalThis.MediaRecorder === 'undefined') {
      throw new Error('当前浏览器不支持音频录制')
    }
  }

  async requestMicrophone() {
    this.ensureSupported()

    if (this.stream?.active) {
      return this.stream
    }

    try {
      this.stream = await globalThis.navigator.mediaDevices.getUserMedia({ audio: true })
      return this.stream
    } catch (error) {
      throw normalizeRecorderError(error, '无法访问麦克风，请检查浏览器权限')
    }
  }

  async startRecording() {
    this.ensureSupported()

    if (this.mediaRecorder?.state === 'recording') {
      return
    }

    const stream = await this.requestMicrophone()
    const options = this.mimeType ? { mimeType: this.mimeType } : undefined
    this.chunks = []
    this.recordedBlob = null

    try {
      this.mediaRecorder = new MediaRecorder(stream, options)
    } catch (error) {
      throw normalizeRecorderError(error, '当前浏览器无法启动录音')
    }

    this.mediaRecorder.ondataavailable = event => {
      if (event.data && event.data.size > 0) {
        this.chunks.push(event.data)
      }
    }

    this.mediaRecorder.start()
  }

  async stopRecording() {
    if (!this.mediaRecorder) {
      return this.exportBlob()
    }

    if (this.mediaRecorder.state === 'inactive') {
      this.recordedBlob = this.exportBlob()
      return this.recordedBlob
    }

    return new Promise((resolve, reject) => {
      const recorder = this.mediaRecorder

      const cleanup = () => {
        recorder.onstop = null
        recorder.onerror = null
      }

      recorder.onstop = () => {
        cleanup()
        this.recordedBlob = new Blob(this.chunks, {
          type: this.mimeType || DEFAULT_MIME_TYPE,
        })
        resolve(this.recordedBlob)
      }

      recorder.onerror = event => {
        cleanup()
        reject(normalizeRecorderError(event?.error, '录音停止失败'))
      }

      try {
        recorder.stop()
      } catch (error) {
        cleanup()
        reject(normalizeRecorderError(error, '录音停止失败'))
      }
    })
  }

  exportBlob() {
    if (this.recordedBlob) {
      return this.recordedBlob
    }

    return new Blob(this.chunks, {
      type: this.mimeType || DEFAULT_MIME_TYPE,
    })
  }

  destroy() {
    if (this.mediaRecorder && this.mediaRecorder.state !== 'inactive') {
      try {
        this.mediaRecorder.stop()
      } catch (error) {
        // Ignore destroy-time stop errors.
      }
    }

    if (this.mediaRecorder) {
      this.mediaRecorder.ondataavailable = null
      this.mediaRecorder.onstop = null
      this.mediaRecorder.onerror = null
    }

    if (this.stream) {
      this.stream.getTracks().forEach(track => track.stop())
    }

    this.mediaRecorder = null
    this.stream = null
    this.chunks = []
    this.recordedBlob = null
  }
}

export function createRecorder() {
  return new Recorder()
}

export default createRecorder
