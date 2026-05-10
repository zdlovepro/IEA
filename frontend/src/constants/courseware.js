const COURSEWARE_STATUS_MAP = {
  UPLOADED: {
    text: '已上传',
    tone: 'info',
  },
  PARSING: {
    text: '解析中',
    tone: 'warning',
  },
  PARSED: {
    text: '已解析',
    tone: 'info',
  },
  GENERATING_SCRIPT: {
    text: '生成讲稿中',
    tone: 'accent',
  },
  READY: {
    text: '已就绪',
    tone: 'success',
  },
  FAILED: {
    text: '失败',
    tone: 'danger',
  },
}

const TASK_STATUS_MAP = {
  PENDING: '待处理',
  RUNNING: '执行中',
  SUCCESS: '已完成',
  FAILED: '失败',
  PARTIAL_SUCCESS: '部分完成',
}

export function getCoursewareStatusMeta(status) {
  return (
    COURSEWARE_STATUS_MAP[String(status || '').trim()] || {
      text: status || '未知状态',
      tone: 'neutral',
    }
  )
}

export function getTaskStatusLabel(status) {
  if (!status) {
    return '等待调度'
  }

  return TASK_STATUS_MAP[String(status).trim()] || status
}
