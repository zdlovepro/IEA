/**
 * 文件上传相关常量
 */
export const FILE_TYPE = {
  PPT: 'pptx',
  PDF: 'pdf',
}

export const MAX_FILE_SIZE = 100 * 1024 * 1024 // 100MB

export const ALLOWED_EXTENSIONS = ['.pptx', '.pdf']

/**
 * 上传状态
 */
export const UPLOAD_STATUS = {
  PENDING: 'pending', // 等待
  UPLOADING: 'uploading', // 上传中
  PARSING: 'parsing', // 解析中
  SUCCESS: 'success', // 成功
  ERROR: 'error', // 失败
}
