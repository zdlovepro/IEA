import request from '@/utils/request'
import { QA_API } from '@/constants/api'

export function askText({ sessionId, question } = {}) {
  return request.post(QA_API.ASK_TEXT, { sessionId, question })
}
