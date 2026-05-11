export const LECTURE_STATE = {
  IDLE: 'IDLE',
  PLAYING: 'PLAYING',
  INTERRUPTED: 'INTERRUPTED',
  ANSWERING: 'ANSWERING',
  RESUMING: 'RESUMING',
  ENDED: 'ENDED',
}

const LEGACY_STATUS_MAP = {
  idle: LECTURE_STATE.IDLE,
  playing: LECTURE_STATE.PLAYING,
  paused: LECTURE_STATE.INTERRUPTED,
  interrupted: LECTURE_STATE.INTERRUPTED,
  answering: LECTURE_STATE.ANSWERING,
  resuming: LECTURE_STATE.RESUMING,
  finished: LECTURE_STATE.ENDED,
  ended: LECTURE_STATE.ENDED,
  ACTIVE: LECTURE_STATE.PLAYING,
  PAUSED: LECTURE_STATE.INTERRUPTED,
  FINISHED: LECTURE_STATE.ENDED,
}

export const LECTURE_STATUS_MAP = {
  [LECTURE_STATE.IDLE]: {
    text: '待开始',
    className: 'state-idle',
    color: 'info',
  },
  [LECTURE_STATE.PLAYING]: {
    text: '讲解中',
    className: 'state-playing',
    color: 'success',
  },
  [LECTURE_STATE.INTERRUPTED]: {
    text: '倾听中',
    className: 'state-interrupted',
    color: 'warning',
  },
  [LECTURE_STATE.ANSWERING]: {
    text: '答疑中',
    className: 'state-answering',
    color: 'warning',
  },
  [LECTURE_STATE.RESUMING]: {
    text: '恢复中',
    className: 'state-resuming',
    color: 'info',
  },
  [LECTURE_STATE.ENDED]: {
    text: '课程已结束',
    className: 'state-ended',
    color: 'default',
  },
}

export function normalizeLectureStatus(status) {
  if (!status) {
    return LECTURE_STATE.IDLE
  }

  if (Object.values(LECTURE_STATE).includes(status)) {
    return status
  }

  const normalized = String(status).trim()
  if (Object.values(LECTURE_STATE).includes(normalized)) {
    return normalized
  }

  return LEGACY_STATUS_MAP[normalized] || LEGACY_STATUS_MAP[normalized.toLowerCase()] || LECTURE_STATE.IDLE
}
