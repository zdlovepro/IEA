const DEFAULT_THRESHOLD = 0.04
const DEFAULT_SILENCE_DURATION_MS = 2000
const DEFAULT_MIN_SPEECH_DURATION_MS = 160

class VoiceActivityDetector {
  constructor({
    threshold = DEFAULT_THRESHOLD,
    silenceDurationMs = DEFAULT_SILENCE_DURATION_MS,
    minSpeechDurationMs = DEFAULT_MIN_SPEECH_DURATION_MS,
    stream = null,
    getStream = null,
    onSpeechStart = null,
    onSpeechEnd = null,
    onVolumeChange = null,
  } = {}) {
    this.threshold = threshold
    this.silenceDurationMs = silenceDurationMs
    this.minSpeechDurationMs = minSpeechDurationMs
    this.stream = stream
    this.getStream = getStream
    this.onSpeechStart = onSpeechStart
    this.onSpeechEnd = onSpeechEnd
    this.onVolumeChange = onVolumeChange

    this.audioContext = null
    this.sourceNode = null
    this.analyserNode = null
    this.dataArray = null
    this.frameId = 0
    this.isRunning = false
    this.isSpeechActive = false
    this.speechCandidateStartedAt = 0
    this.lastSpeechDetectedAt = 0
  }

  ensureSupported() {
    if (!globalThis.navigator?.mediaDevices?.getUserMedia) {
      throw new Error('当前浏览器不支持麦克风访问')
    }

    if (!globalThis.AudioContext && !globalThis.webkitAudioContext) {
      throw new Error('当前浏览器不支持语音活动检测')
    }
  }

  async resolveStream() {
    if (this.stream?.active) {
      return this.stream
    }

    if (typeof this.getStream === 'function') {
      this.stream = await this.getStream()
      return this.stream
    }

    this.stream = await globalThis.navigator.mediaDevices.getUserMedia({ audio: true })
    return this.stream
  }

  async start() {
    this.ensureSupported()

    if (this.isRunning) {
      return
    }

    const stream = await this.resolveStream()
    const AudioContextCtor = globalThis.AudioContext || globalThis.webkitAudioContext

    if (!this.audioContext || this.audioContext.state === 'closed') {
      this.audioContext = new AudioContextCtor()
    }

    if (this.audioContext.state === 'suspended') {
      await this.audioContext.resume()
    }

    this.sourceNode?.disconnect()
    this.analyserNode?.disconnect()

    this.sourceNode = this.audioContext.createMediaStreamSource(stream)
    this.analyserNode = this.audioContext.createAnalyser()
    this.analyserNode.fftSize = 2048
    this.dataArray = new Uint8Array(this.analyserNode.fftSize)

    this.sourceNode.connect(this.analyserNode)
    this.isRunning = true
    this.isSpeechActive = false
    this.speechCandidateStartedAt = 0
    this.lastSpeechDetectedAt = 0

    this.tick()
  }

  tick() {
    if (!this.isRunning || !this.analyserNode || !this.dataArray) {
      return
    }

    this.analyserNode.getByteTimeDomainData(this.dataArray)

    let sumSquares = 0
    for (let index = 0; index < this.dataArray.length; index += 1) {
      const normalized = (this.dataArray[index] - 128) / 128
      sumSquares += normalized * normalized
    }

    const volume = Math.sqrt(sumSquares / this.dataArray.length)
    const now = performance.now()

    this.onVolumeChange?.(volume)

    if (volume >= this.threshold) {
      if (!this.speechCandidateStartedAt) {
        this.speechCandidateStartedAt = now
      }

      if (!this.isSpeechActive && now - this.speechCandidateStartedAt >= this.minSpeechDurationMs) {
        this.isSpeechActive = true
        this.lastSpeechDetectedAt = now
        Promise.resolve(this.onSpeechStart?.())
      } else if (this.isSpeechActive) {
        this.lastSpeechDetectedAt = now
      }
    } else {
      this.speechCandidateStartedAt = 0
      if (this.isSpeechActive && now - this.lastSpeechDetectedAt >= this.silenceDurationMs) {
        this.isSpeechActive = false
        this.lastSpeechDetectedAt = 0
        Promise.resolve(this.onSpeechEnd?.())
      }
    }

    this.frameId = globalThis.requestAnimationFrame(() => this.tick())
  }

  stop() {
    if (this.frameId) {
      globalThis.cancelAnimationFrame(this.frameId)
      this.frameId = 0
    }

    this.isRunning = false
    this.isSpeechActive = false
    this.speechCandidateStartedAt = 0
    this.lastSpeechDetectedAt = 0

    this.sourceNode?.disconnect()
    this.analyserNode?.disconnect()
    this.sourceNode = null
    this.analyserNode = null
    this.dataArray = null
  }

  async destroy() {
    this.stop()

    if (this.audioContext && this.audioContext.state !== 'closed') {
      await this.audioContext.close()
    }

    this.audioContext = null
    this.stream = null
  }
}

export function createVAD(options = {}) {
  return new VoiceActivityDetector(options)
}

export default createVAD
