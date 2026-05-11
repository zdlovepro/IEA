import request from '@/utils/request'
import { QA_API } from '@/constants/api'
import { createSseClient } from '@/utils/sseClient'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

const joinApiUrl = path => {
  if (!path) {
    return API_BASE_URL
  }

  const normalizedBase = API_BASE_URL.endsWith('/') ? API_BASE_URL.slice(0, -1) : API_BASE_URL
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${normalizedBase}${normalizedPath}`
}

export function askText({ sessionId, question } = {}) {
  return request.post(QA_API.ASK_TEXT, { sessionId, question })
}

export function buildQaStreamUrl({ sessionId, question, pageIndex } = {}) {
  const searchParams = new URLSearchParams()

  if (sessionId) {
    searchParams.set('sessionId', sessionId)
  }

  if (question) {
    searchParams.set('question', question)
  }

  if (pageIndex !== undefined && pageIndex !== null) {
    searchParams.set('pageIndex', String(pageIndex))
  }

  const baseUrl = joinApiUrl(QA_API.STREAM)
  const queryString = searchParams.toString()
  return queryString ? `${baseUrl}?${queryString}` : baseUrl
}

export function streamAskText({ sessionId, question, pageIndex } = {}, handlers = {}) {
  const client = createSseClient()
  client.connect(joinApiUrl(QA_API.STREAM), { sessionId, question, pageIndex }, handlers)
  return client
}
