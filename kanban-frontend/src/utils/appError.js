export class AppError extends Error {
  constructor(message, options = {}) {
    super(message || '请求处理失败')
    this.name = 'AppError'
    this.code = options.code ?? null
    this.status = options.status ?? null
    this.kind = options.kind || 'UNKNOWN'
    this.retryable = Boolean(options.retryable)
    this.response = options.response
    this.config = options.config || options.response?.config
    this.originalError = options.originalError
  }
}

function errorKind(status, hasResponse) {
  if (!hasResponse) return 'NETWORK'
  if (status === 401) return 'AUTH'
  if (status === 403) return 'PERMISSION'
  if (status === 404) return 'NOT_FOUND'
  if (status === 409) return 'CONFLICT'
  if (status === 400 || status === 422) return 'VALIDATION'
  if (status >= 500) return 'SERVER'
  return 'BUSINESS'
}

export function normalizeApiError(error) {
  if (error instanceof AppError) return error
  const response = error?.response
  const bodyCode = response?.data?.code
  const status = response?.status || (bodyCode && bodyCode !== 200 ? bodyCode : null)
  const message = response?.data?.msg || error?.message || (response ? '请求处理失败' : '暂时无法连接服务器，请检查网络或稍后重试')
  return new AppError(message, {
    code: bodyCode ?? null,
    status,
    kind: errorKind(status, Boolean(response)),
    retryable: !response || status >= 500,
    response,
    config: error?.config || response?.config,
    originalError: error
  })
}

export function createMessageDeduper(windowMs = 1500, now = () => Date.now()) {
  const seen = new Map()
  return message => {
    const time = now()
    const previous = seen.get(message)
    if (previous !== undefined && time - previous < windowMs) return false
    seen.set(message, time)
    for (const [key, timestamp] of seen) if (time - timestamp >= windowMs) seen.delete(key)
    return true
  }
}
