import request from '@/utils/request'
import { COURSEWARE_API, SCRIPT_API } from '@/constants/api'

export function uploadCourseware(file, name, config = {}) {
  const formData = new FormData()
  formData.append('file', file)
  if (name) {
    formData.append('name', name)
  }

  return request.post(COURSEWARE_API.UPLOAD, formData, {
    ...config,
    headers: {
      'Content-Type': 'multipart/form-data',
      ...(config.headers || {}),
    },
  })
}

export function listCourseware(params = {}) {
  return request.get(COURSEWARE_API.LIST, { params })
}

export function getCoursewareDetail(coursewareId) {
  return request.get(COURSEWARE_API.DETAIL(coursewareId))
}

export function getCoursewareScript(coursewareId) {
  return request.get(SCRIPT_API.GET(coursewareId))
}

export function generateScript(coursewareId) {
  return request.post(SCRIPT_API.GENERATE(coursewareId))
}
