class AudioPlayer {
  constructor() {
    this.audio = null
    this.currentUrl = ''
    this.listeners = {
      ended: new Set(),
      timeupdate: new Set(),
      error: new Set(),
    }
    this.boundHandlers = {
      ended: () => {
        this.listeners.ended.forEach(callback => callback())
      },
      timeupdate: () => {
        this.listeners.timeupdate.forEach(callback => callback())
      },
      error: event => {
        this.listeners.error.forEach(callback => callback(event))
      },
    }
  }

  ensureAudio() {
    if (this.audio) {
      return this.audio
    }

    const audio = new Audio()
    audio.preload = 'auto'
    audio.addEventListener('ended', this.boundHandlers.ended)
    audio.addEventListener('timeupdate', this.boundHandlers.timeupdate)
    audio.addEventListener('error', this.boundHandlers.error)
    this.audio = audio
    return audio
  }

  async load(url) {
    if (!url) {
      const error = new Error('音频地址为空')
      this.listeners.error.forEach(callback => callback(error))
      throw error
    }

    const audio = this.ensureAudio()
    this.stop()
    this.currentUrl = url

    return new Promise((resolve, reject) => {
      const cleanup = () => {
        audio.removeEventListener('loadedmetadata', handleLoaded)
        audio.removeEventListener('error', handleError)
      }

      const handleLoaded = () => {
        cleanup()
        resolve({
          duration: this.getDuration(),
        })
      }

      const handleError = event => {
        cleanup()
        reject(event instanceof Error ? event : new Error('音频加载失败'))
      }

      audio.addEventListener('loadedmetadata', handleLoaded, { once: true })
      audio.addEventListener('error', handleError, { once: true })
      audio.src = url
      audio.load()
    })
  }

  async play() {
    const audio = this.ensureAudio()
    await audio.play()
  }

  pause() {
    if (!this.audio) {
      return
    }

    this.audio.pause()
  }

  async resume() {
    if (!this.audio) {
      return
    }

    await this.audio.play()
  }

  stop() {
    if (!this.audio) {
      return
    }

    this.audio.pause()
    this.audio.currentTime = 0
  }

  seek(seconds) {
    if (!this.audio) {
      return
    }

    const duration = this.getDuration()
    const nextTime = Number(seconds)
    if (!Number.isFinite(nextTime)) {
      return
    }

    const clamped = duration > 0 ? Math.min(Math.max(nextTime, 0), duration) : Math.max(nextTime, 0)
    this.audio.currentTime = clamped
  }

  getCurrentTime() {
    if (!this.audio) {
      return 0
    }

    return Number.isFinite(this.audio.currentTime) ? this.audio.currentTime : 0
  }

  getDuration() {
    if (!this.audio) {
      return 0
    }

    return Number.isFinite(this.audio.duration) ? this.audio.duration : 0
  }

  onEnded(callback) {
    this.listeners.ended.add(callback)
    return () => {
      this.listeners.ended.delete(callback)
    }
  }

  onTimeUpdate(callback) {
    this.listeners.timeupdate.add(callback)
    return () => {
      this.listeners.timeupdate.delete(callback)
    }
  }

  onError(callback) {
    this.listeners.error.add(callback)
    return () => {
      this.listeners.error.delete(callback)
    }
  }

  destroy() {
    if (this.audio) {
      this.audio.pause()
      this.audio.removeEventListener('ended', this.boundHandlers.ended)
      this.audio.removeEventListener('timeupdate', this.boundHandlers.timeupdate)
      this.audio.removeEventListener('error', this.boundHandlers.error)
      this.audio.removeAttribute('src')
      this.audio.load()
    }

    this.audio = null
    this.currentUrl = ''
    Object.values(this.listeners).forEach(listenerSet => listenerSet.clear())
  }
}

const audioPlayer = new AudioPlayer()

export default audioPlayer
