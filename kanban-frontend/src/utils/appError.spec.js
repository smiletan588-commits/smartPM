import { describe, expect, it } from 'vitest'
import { AppError, createMessageDeduper, normalizeApiError } from './appError'

describe('AppError', () => {
  it('keeps backend business messages returned with HTTP 200', () => {
    const error = normalizeApiError({ response: { status: 200, data: { code: 403, msg: '只有项目负责人可以删除项目' } } })
    expect(error).toBeInstanceOf(AppError)
    expect(error.status).toBe(200)
    expect(error.code).toBe(403)
    expect(error.message).toBe('只有项目负责人可以删除项目')
  })

  it('turns missing responses into retryable network errors', () => {
    const error = normalizeApiError(new Error('Network Error'))
    expect(error.kind).toBe('NETWORK')
    expect(error.retryable).toBe(true)
  })

  it('deduplicates the same message inside the notification window', () => {
    let time = 1000
    const allow = createMessageDeduper(1500, () => time)
    expect(allow('失败')).toBe(true)
    expect(allow('失败')).toBe(false)
    time = 2500
    expect(allow('失败')).toBe(true)
  })
})
