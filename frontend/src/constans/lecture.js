/**
 * 讲课主界面相关的常量
 */
export const LECTURE_STATE = {
  IDLE: 'idle', // 未开始
  PLAYING: 'playing', // 播放中
  PAUSED: 'paused', // 暂停
  FINISHED: 'finished', // 完成
}

/**
 * 问答相关常量
 */
export const QA_TYPE = {
  TEXT: 'text', // 文本问答
  VOICE: 'voice', // 语音问答
}

/**
 * 理解度等级
 */
export const COMPREHENSION_LEVEL = {
  LOW: 'low', // 低
  MEDIUM: 'medium', // 中
  HIGH: 'high', // 高
}

/**
 * 讲解节奏
 */
export const PACE_MODE = {
  FAST: 'fast', // 快速
  NORMAL: 'normal', // 正常
  SLOW: 'slow', // 细讲
}
