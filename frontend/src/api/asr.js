import request from '@/utils/request'
import { ASR_API } from '@/constants/api'

export function recognizeAudio({ file, sessionId, pageIndex }, config = {}) {
  const formData = new FormData()
  formData.append('file', file)

  if (sessionId) {
    formData.append('sessionId', sessionId)
  }

  if (pageIndex !== undefined && pageIndex !== null) {
    formData.append('pageIndex', String(pageIndex))
  }

  return request.post(ASR_API.RECOGNIZE, formData, {
    ...config,
    headers: {
      'Content-Type': 'multipart/form-data',
      ...(config.headers || {}),
    },
  })
}
