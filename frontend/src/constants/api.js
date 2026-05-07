/**
 * API 端点常量
 */

// 用户相关
export const USER_API = {
  LOGIN: '/user/login',
  LOGOUT: '/user/logout',
  PROFILE: '/user/profile',
  UPDATE: '/user/update',
}

// 课件相关
export const COURSEWARE_API = {
  UPLOAD: '/courseware/upload',
  LIST: '/courseware',
  DETAIL: id => `/courseware/${id}`,
}

// 讲稿相关
export const SCRIPT_API = {
  // 获取讲稿（根据课件ID）
  GET: coursewareId => `/courseware/${coursewareId}/script`,
  // 触发讲稿生成
  GENERATE: coursewareId => `/courseware/${coursewareId}/script/generate`,
}

// 讲课相关
export const LECTURE_API = {
  START: '/lecture/start',
  PAUSE: sessionId => `/lecture/${sessionId}/pause`,
  RESUME: '/lecture/resume',
}

// 问答相关
export const QA_API = {
  ASK_TEXT: '/qa/ask-text',
}

// 其他 API 可以继续添加
export const OTHER_API = {
  // 示例
}
