function buildUrlWithParams(url, params = {}) {
  const baseUrl =
    url.startsWith('http://') || url.startsWith('https://')
      ? new URL(url)
      : new URL(url, globalThis.location?.origin || 'http://localhost')

  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') {
      return
    }
    baseUrl.searchParams.set(key, String(value))
  })

  if (url.startsWith('http://') || url.startsWith('https://')) {
    return baseUrl.toString()
  }

  return `${baseUrl.pathname}${baseUrl.search}`
}

export function createSseClient() {
  let eventSource = null
  let manuallyClosed = false

  const close = () => {
    manuallyClosed = true
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
  }

  const connect = (url, params = {}, handlers = {}) => {
    if (typeof globalThis.EventSource === 'undefined') {
      throw new Error('当前浏览器不支持流式问答。')
    }

    close()
    manuallyClosed = false

    const targetUrl = buildUrlWithParams(url, params)
    eventSource = new EventSource(targetUrl)

    eventSource.onmessage = event => {
      if (!event?.data) {
        return
      }

      try {
        const payload = JSON.parse(event.data)
        if (payload.type === 'delta') {
          handlers.onDelta?.(payload.content || '')
          return
        }

        if (payload.type === 'done') {
          handlers.onDone?.()
          close()
          return
        }

        if (payload.type === 'error') {
          const error = new Error(payload.message || '流式问答失败，请稍后重试。')
          handlers.onError?.(error)
          close()
        }
      } catch (error) {
        handlers.onError?.(new Error('流式响应解析失败。'))
        close()
      }
    }

    eventSource.onerror = () => {
      if (manuallyClosed) {
        return
      }

      handlers.onError?.(new Error('流式连接失败，请稍后重试。'))
      close()
    }

    return eventSource
  }

  return {
    connect,
    close,
  }
}
