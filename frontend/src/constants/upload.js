export const FILE_TYPE = {
  PPT_LEGACY: 'ppt',
  PPT: 'pptx',
  PDF: 'pdf',
}

export const MAX_FILE_SIZE = 100 * 1024 * 1024

export const ALLOWED_EXTENSIONS = ['.ppt', '.pptx', '.pdf']

export const UPLOAD_STATUS = {
  PENDING: 'pending',
  UPLOADING: 'uploading',
  PARSING: 'parsing',
  SUCCESS: 'success',
  ERROR: 'error',
}
