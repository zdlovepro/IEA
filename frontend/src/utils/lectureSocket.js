const DEFAULT_HEARTBEAT_INTERVAL = 20_000
const DEFAULT_RECONNECT_DELAY = 1_500
const DEFAULT_MAX_RECONNECT_ATTEMPTS = 3

function createSocketError(message, code) {
  const error = new Error(message)
  if (code) {
    error.code = code
  }
  return error
}

function resolveSocketUrl(sessionId) {
  const protocol = globalThis.location?.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = globalThis.location?.host

  if (!host) {
    throw createSocketError('无法确定信令服务地址。', 'WS_URL_RESOLVE_FAILED')
  }

  const query = new URLSearchParams({ sessionId: String(sessionId || '') })
  return `${protocol}//${host}/ws/lecture?${query.toString()}`
}

export function createLectureSocket(options = {}) {
  const heartbeatIntervalMs = options.heartbeatIntervalMs || DEFAULT_HEARTBEAT_INTERVAL
  const reconnectDelayMs = options.reconnectDelayMs || DEFAULT_RECONNECT_DELAY
  const maxReconnectAttempts = options.maxReconnectAttempts || DEFAULT_MAX_RECONNECT_ATTEMPTS

  let socket = null
  let activeSessionId = ''
  let heartbeatTimer = null
  let reconnectTimer = null
  let reconnectAttempts = 0
  let manualClose = false
  let connectPromise = null
  const messageListeners = new Set()
  const errorListeners = new Set()

  const clearHeartbeat = () => {
    if (heartbeatTimer) {
      globalThis.clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  const clearReconnect = () => {
    if (reconnectTimer) {
      globalThis.clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }

  const emitMessage = payload => {
    messageListeners.forEach(listener => {
      listener(payload)
    })
  }

  const emitError = error => {
    errorListeners.forEach(listener => {
      listener(error)
    })
  }

  const resetSocketState = () => {
    clearHeartbeat()
    clearReconnect()
    connectPromise = null
  }

  const scheduleReconnect = () => {
    if (manualClose || reconnectAttempts >= maxReconnectAttempts || !activeSessionId) {
      emitError(createSocketError('信令连接失败，可继续手动提问。', 'WS_RECONNECT_EXHAUSTED'))
      return
    }

    reconnectAttempts += 1
    clearReconnect()
    reconnectTimer = globalThis.setTimeout(() => {
      connect(activeSessionId).catch(() => {
        // reconnect failure is surfaced through onerror/onclose handlers
      })
    }, reconnectDelayMs * reconnectAttempts)
  }

  const heartbeat = () => {
    if (!socket || socket.readyState !== WebSocket.OPEN || !activeSessionId) {
      return false
    }

    socket.send(
      JSON.stringify({
        type: 'heartbeat',
        sessionId: activeSessionId,
      }),
    )
    return true
  }

  const startHeartbeat = () => {
    clearHeartbeat()
    heartbeatTimer = globalThis.setInterval(() => {
      heartbeat()
    }, heartbeatIntervalMs)
  }

  const close = () => {
    manualClose = true
    resetSocketState()

    if (socket) {
      const currentSocket = socket
      socket = null
      if (
        currentSocket.readyState === WebSocket.OPEN ||
        currentSocket.readyState === WebSocket.CONNECTING
      ) {
        currentSocket.close()
      }
    }
  }

  const send = (type, payload = {}) => {
    if (!socket || socket.readyState !== WebSocket.OPEN) {
      return false
    }

    socket.send(
      JSON.stringify({
        type,
        ...payload,
      }),
    )
    return true
  }

  const connect = sessionId => {
    if (!sessionId) {
      return Promise.reject(createSocketError('sessionId 不能为空。', 'WS_SESSION_REQUIRED'))
    }

    if (typeof globalThis.WebSocket === 'undefined') {
      return Promise.reject(createSocketError('当前浏览器不支持 WebSocket。', 'WS_UNSUPPORTED'))
    }

    if (
      socket &&
      socket.readyState === WebSocket.OPEN &&
      activeSessionId === sessionId
    ) {
      return Promise.resolve()
    }

    if (connectPromise && activeSessionId === sessionId) {
      return connectPromise
    }

    manualClose = false
    activeSessionId = sessionId
    resetSocketState()

    if (socket) {
      const previousSocket = socket
      socket = null
      if (
        previousSocket.readyState === WebSocket.OPEN ||
        previousSocket.readyState === WebSocket.CONNECTING
      ) {
        previousSocket.close()
      }
    }

    connectPromise = new Promise((resolve, reject) => {
      let isSettled = false
      let didOpen = false

      const resolveOnce = value => {
        if (isSettled) {
          return
        }
        isSettled = true
        resolve(value)
      }

      const rejectOnce = error => {
        if (isSettled) {
          return
        }
        isSettled = true
        reject(error)
      }

      try {
        socket = new WebSocket(resolveSocketUrl(sessionId))
      } catch (error) {
        connectPromise = null
        rejectOnce(error)
        return
      }

      socket.onopen = () => {
        didOpen = true
        reconnectAttempts = 0
        startHeartbeat()
        emitMessage({ type: 'connection', payload: { status: 'open', sessionId } })
        resolveOnce()
      }

      socket.onmessage = event => {
        try {
          const payload = JSON.parse(event.data)
          emitMessage(payload)
        } catch (error) {
          emitError(createSocketError('信令消息解析失败。', 'WS_MESSAGE_PARSE_FAILED'))
        }
      }

      socket.onerror = () => {
        const error = createSocketError('信令连接失败，可继续手动提问。', 'WS_ERROR')
        emitError(error)
        if (!didOpen) {
          rejectOnce(error)
        }
      }

      socket.onclose = event => {
        resetSocketState()
        emitMessage({
          type: 'connection',
          payload: {
            status: 'closed',
            sessionId,
            code: event.code,
          },
        })

        if (manualClose) {
          return
        }

        if (!didOpen) {
          rejectOnce(createSocketError('信令连接失败，可继续手动提问。', 'WS_CONNECT_CLOSED'))
        }

        scheduleReconnect()
      }
    })

    return connectPromise
  }

  const onMessage = callback => {
    if (typeof callback !== 'function') {
      return () => {}
    }
    messageListeners.add(callback)
    return () => {
      messageListeners.delete(callback)
    }
  }

  const onError = callback => {
    if (typeof callback !== 'function') {
      return () => {}
    }
    errorListeners.add(callback)
    return () => {
      errorListeners.delete(callback)
    }
  }

  return {
    connect,
    send,
    heartbeat,
    close,
    onMessage,
    onError,
  }
}
